package de.tum.cit.aet.artemis.core.security.websocket;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess.AnyAuthenticatedUser;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess.AtLeastRoleInCourse;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess.AtLeastRoleInExercise;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess.AtLeastRoleInLecture;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess.Custom;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess.ElevatedAdministrator;
import de.tum.cit.aet.artemis.core.security.websocket.WebsocketTopicAccess.OwnUser;
import de.tum.cit.aet.artemis.core.service.ElevatedAccessService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

/**
 * Knows every websocket topic the active modules declare and decides whether a subscription is allowed.
 * <p>
 * The decision is deny by default: a subscription is only accepted for a destination that exactly one declared topic matches, and for a {@link WebsocketTopic} only if its
 * {@link WebsocketTopicAccess} rule admits the subscriber. Destinations of internal broker topics, destinations another session's user topic resolves to, and pattern
 * subscriptions are therefore all rejected without any rule for them.
 * <p>
 * The topics are collected from the {@link WebsocketTopicProvider} beans the first time the registry is used, which is the first subscription after startup.
 */
@Profile(PROFILE_CORE)
@Lazy
@Component
public class WebsocketTopicRegistry {

    private static final Logger log = LoggerFactory.getLogger(WebsocketTopicRegistry.class);

    /**
     * The prefix of per-user destinations a client subscribes to, e.g. {@code /user/topic/newResults}.
     */
    public static final String USER_DESTINATION_PREFIX = "/user/";

    /**
     * Characters that turn a destination into a pattern over many topics: {@code *}, {@code ?}, {@code {}} for the Ant-style matching of the simple broker, {@code *},
     * {@code #} and {@code >} for the topic wildcards of an external STOMP broker.
     */
    private static final Pattern DESTINATION_WILDCARD = Pattern.compile("[*?{}#>]");

    /**
     * The outcome of a subscription check.
     */
    public enum Decision {
        /** A declared topic matches and admits the subscriber. */
        ALLOWED,
        /** A declared topic matches, but its access rule does not admit the subscriber. */
        DENIED,
        /** No declared topic matches the destination. */
        UNDECLARED,
        /** The subscription has no destination, no authenticated subscriber, or a pattern destination. */
        INVALID
    }

    private record DeclaredTopic(WebsocketTopic topic, WebsocketTopicProvider provider) {
    }

    private final List<DeclaredTopic> topics;

    private final List<WebsocketUserTopic> userTopics;

    private final UserRepository userRepository;

    private final ExerciseRepository exerciseRepository;

    private final ElevatedAccessService elevatedAccessService;

    public WebsocketTopicRegistry(List<WebsocketTopicProvider> providers, UserRepository userRepository, ExerciseRepository exerciseRepository,
            ElevatedAccessService elevatedAccessService) {
        this.userRepository = userRepository;
        this.exerciseRepository = exerciseRepository;
        this.elevatedAccessService = elevatedAccessService;

        List<DeclaredTopic> declaredTopics = new ArrayList<>();
        List<WebsocketUserTopic> declaredUserTopics = new ArrayList<>();
        for (WebsocketTopicProvider provider : providers) {
            Class<?> providerClass = AopUtils.getTargetClass(provider);
            for (WebsocketTopic topic : constantsOf(providerClass, WebsocketTopic.class)) {
                if (topic.access() instanceof Custom<?> custom && !custom.provider().isAssignableFrom(providerClass)) {
                    throw new IllegalStateException("The websocket topic " + topic.template() + " declared in " + providerClass.getName() + " uses a custom check of "
                            + custom.provider().getName() + "; a custom check must be implemented by the declaring provider");
                }
                declaredTopics.add(new DeclaredTopic(topic, provider));
            }
            declaredUserTopics.addAll(constantsOf(providerClass, WebsocketUserTopic.class));
        }
        requireUniqueTemplates(declaredTopics.stream().map(declared -> declared.topic().template()).toList());
        requireUniqueTemplates(declaredUserTopics.stream().map(WebsocketUserTopic::template).toList());
        this.topics = List.copyOf(declaredTopics);
        this.userTopics = List.copyOf(declaredUserTopics);
        // Every destination has to resolve to exactly one topic, otherwise the access rule of a topic could be shadowed by another one
        for (DeclaredTopic declared : topics) {
            String sample = declared.topic().sampleDestination();
            requireResolvesTo(declared, findMostSpecific(topics, candidate -> candidate.topic().match(sample), candidate -> candidate.topic().literalCount(), sample), sample);
        }
        for (WebsocketUserTopic userTopic : userTopics) {
            String sample = userTopic.sampleDestination();
            requireResolvesTo(userTopic, findMostSpecific(userTopics, candidate -> candidate.match(sample), WebsocketUserTopic::literalCount, sample), sample);
        }
        log.debug("Registered {} websocket topics and {} websocket user topics", topics.size(), userTopics.size());
    }

    /**
     * Decides whether the subscriber may subscribe to the destination.
     *
     * @param subscriber  the principal of the websocket session
     * @param destination the destination of the SUBSCRIBE frame
     * @return the decision; everything but {@link Decision#ALLOWED} rejects the subscription
     */
    public Decision authorizeSubscription(@Nullable Principal subscriber, @Nullable String destination) {
        if (subscriber == null || subscriber instanceof AnonymousAuthenticationToken || destination == null || DESTINATION_WILDCARD.matcher(destination).find()) {
            return Decision.INVALID;
        }
        if (destination.startsWith(USER_DESTINATION_PREFIX)) {
            // Spring delivers a user destination only to the sessions of the addressed user, so a declared user topic needs no further check.
            String topicDestination = destination.substring(USER_DESTINATION_PREFIX.length() - 1);
            return findMostSpecific(userTopics, topic -> topic.match(topicDestination), WebsocketUserTopic::literalCount, topicDestination).isPresent() ? Decision.ALLOWED
                    : Decision.UNDECLARED;
        }
        var match = findMostSpecific(topics, declared -> declared.topic().match(destination), declared -> declared.topic().literalCount(), destination);
        if (match.isEmpty()) {
            return Decision.UNDECLARED;
        }
        DeclaredTopic declared = match.get().topic();
        var subscription = new WebsocketSubscription(subscriber, destination, match.get().variables(), () -> hasAdministratorAccess(subscriber));
        try {
            return isAllowed(declared, subscription) ? Decision.ALLOWED : Decision.DENIED;
        }
        catch (RuntimeException e) {
            // A malformed id, or an entity that does not exist. Neither may open the topic.
            log.debug("The access check of the websocket topic {} failed for {}: {}", declared.topic().template(), destination, e.getMessage());
            return Decision.DENIED;
        }
    }

    private boolean isAllowed(DeclaredTopic declared, WebsocketSubscription subscription) {
        String login = subscription.login();
        return switch (declared.topic().access()) {
            case AnyAuthenticatedUser _ -> true;
            case ElevatedAdministrator _ -> subscription.hasAdministratorAccess();
            case OwnUser(String userIdVariable) -> {
                long userId = subscription.id(userIdVariable);
                yield userRepository.findIdByLogin(login).filter(id -> id == userId).isPresent();
            }
            case AtLeastRoleInCourse(CourseRole minimum, String courseIdVariable) ->
                userRepository.existsByLoginInCourseWithMinRole(login, subscription.id(courseIdVariable), CourseRole.valuesAtLeast(minimum))
                        || subscription.hasAdministratorAccess();
            case AtLeastRoleInExercise(CourseRole minimum, CourseRole minimumForExamExercises, String exerciseIdVariable) -> {
                long exerciseId = subscription.id(exerciseIdVariable);
                CourseRole required = minimum != minimumForExamExercises && exerciseRepository.isExamExercise(exerciseId) ? minimumForExamExercises : minimum;
                yield userRepository.existsByLoginInExerciseWithMinRole(login, exerciseId, CourseRole.valuesAtLeast(required)) || subscription.hasAdministratorAccess();
            }
            case AtLeastRoleInLecture(CourseRole minimum, String lectureIdVariable) ->
                userRepository.existsByLoginInLectureWithMinRole(login, subscription.id(lectureIdVariable), CourseRole.valuesAtLeast(minimum))
                        || subscription.hasAdministratorAccess();
            case Custom<?> custom -> custom.isAllowed(declared.provider(), subscription);
        };
    }

    private boolean hasAdministratorAccess(Principal subscriber) {
        // Use the websocket session's authentication, never an unrelated or absent thread SecurityContext.
        return subscriber instanceof Authentication authentication && elevatedAccessService.isAdminElevationActive(authentication);
    }

    private record Match<T>(T topic, Map<String, String> variables) {
    }

    /**
     * Finds the matching topic with the most literal segments, so that {@code /topic/iris/struggle-intervention} wins over {@code /topic/iris/{sessionId}}. Two matches
     * with the same number of literal segments are ambiguous and match nothing.
     */
    private static <T> Optional<Match<T>> findMostSpecific(List<T> candidates, Function<T, Optional<Map<String, String>>> matcher, Function<T, Integer> specificity,
            String destination) {
        List<Match<T>> matches = new ArrayList<>(1);
        for (T candidate : candidates) {
            matcher.apply(candidate).ifPresent(variables -> matches.add(new Match<>(candidate, variables)));
        }
        if (matches.size() <= 1) {
            return matches.stream().findFirst();
        }
        matches.sort(Comparator.comparing((Match<T> match) -> specificity.apply(match.topic())).reversed());
        if (specificity.apply(matches.get(0).topic()).equals(specificity.apply(matches.get(1).topic()))) {
            log.error("The websocket destination {} matches several declared topics equally well: {} and {}", destination, matches.get(0).topic(), matches.get(1).topic());
            return Optional.empty();
        }
        return Optional.of(matches.getFirst());
    }

    private static <T> List<T> constantsOf(Class<?> providerClass, Class<T> constantType) {
        List<T> constants = new ArrayList<>();
        for (Field field : providerClass.getDeclaredFields()) {
            if (!constantType.equals(field.getType())) {
                continue;
            }
            int modifiers = field.getModifiers();
            if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)) {
                throw new IllegalStateException("The websocket topic " + providerClass.getName() + "." + field.getName() + " must be public static final");
            }
            try {
                constants.add(constantType.cast(field.get(null)));
            }
            catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot read the websocket topic " + providerClass.getName() + "." + field.getName(), e);
            }
        }
        return constants;
    }

    private static <T> void requireResolvesTo(T topic, Optional<Match<T>> match, String sample) {
        if (match.isEmpty() || match.get().topic() != topic) {
            throw new IllegalStateException("The websocket destination " + sample + " does not resolve to its topic " + topic + " alone; make the templates distinct");
        }
    }

    private static void requireUniqueTemplates(List<String> templates) {
        Set<String> seen = new HashSet<>();
        for (String template : templates) {
            if (!seen.add(template)) {
                throw new IllegalStateException("The websocket topic " + template + " is declared twice");
            }
        }
    }
}
