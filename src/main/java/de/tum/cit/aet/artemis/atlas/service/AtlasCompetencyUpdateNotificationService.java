package de.tum.cit.aet.artemis.atlas.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.atlas.config.AtlasLLMEnabled;
import de.tum.cit.aet.artemis.atlas.dto.AppliedActionDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyOrchestrationResultDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.notification.domain.course_notifications.AtlasCompetencyUpdateNotification;
import de.tum.cit.aet.artemis.notification.dto.payload.AtlasCompetencyUpdatePayloadDTO;
import de.tum.cit.aet.artemis.notification.service.CourseNotificationService;

/**
 * Reports the outcome of an automatic competency orchestration run to the course's instructors and to administrators
 * through the {@link AtlasCompetencyUpdateNotification}, which is e-mail only and opt-in per course.
 * <p>
 * Runs that changed the course, stopped after partial changes, or failed are reported. Runs that ended without a
 * change ({@code NO_OP}, or {@code SUCCESS} without an applied action) and runs deferred by a concurrent run
 * ({@code IN_PROGRESS}) are not, so an opted-in instructor only hears about runs that need their attention.
 */
@Conditional(AtlasLLMEnabled.class)
@Lazy
@Service
public class AtlasCompetencyUpdateNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AtlasCompetencyUpdateNotificationService.class);

    /** Upper bound on the changes listed in one e-mail; the remainder is only counted, so a large run cannot produce an unbounded message. */
    static final int MAX_LISTED_CHANGES = 50;

    /** The ASCII punctuation CommonMark lets a backslash escape; escaping all of it renders model and instructor text literally. */
    private static final String MARKDOWN_PUNCTUATION = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";

    /** How a reported run ended, stored as the {@code outcome} of the notification payload. */
    enum Outcome {
        COMPLETED, PARTIAL, FAILED
    }

    private final CourseNotificationService courseNotificationService;

    private final UserRepository userRepository;

    private final CourseRepository courseRepository;

    public AtlasCompetencyUpdateNotificationService(CourseNotificationService courseNotificationService, UserRepository userRepository, CourseRepository courseRepository) {
        this.courseNotificationService = courseNotificationService;
        this.userRepository = userRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * Sends the report of an automatic run to the course's instructors and to administrators. The notification settings
     * filter this set down to the users who switched the e-mail on for the course.
     * <p>
     * Best effort: every failure is logged and swallowed, so reporting can never change how the scheduler treats the
     * batch (requeue, reservation refund).
     *
     * @param courseId      the course the run belonged to
     * @param exerciseCount the number of changed exercises in the claimed batch
     * @param result        the result of the run, or {@code null} when the run threw before it changed anything
     */
    public void notifyAfterAutomaticRun(long courseId, int exerciseCount, @Nullable CompetencyOrchestrationResultDTO result) {
        Optional<Outcome> outcome = reportedOutcome(result);
        if (outcome.isEmpty()) {
            return;
        }
        try {
            Optional<Course> course = courseRepository.findById(courseId);
            if (course.isEmpty()) {
                log.debug("atlas.automatic course {} no longer exists; skipping competency update notification", courseId);
                return;
            }
            List<User> recipients = findEligibleRecipients(course.get());
            if (recipients.isEmpty()) {
                return;
            }
            List<AppliedActionDTO> appliedActions = result == null ? List.of() : result.appliedActions();
            AtlasCompetencyUpdatePayloadDTO payload = buildPayload(outcome.get(), exerciseCount, appliedActions);
            var notification = new AtlasCompetencyUpdateNotification(courseId, course.get().getTitle(), course.get().getCourseIcon(), payload);
            courseNotificationService.sendCourseNotification(notification, recipients);
        }
        catch (Exception ex) {
            log.warn("atlas.automatic failed to send the competency update notification for course {}: {}", courseId, ex.getMessage(), ex);
        }
    }

    /**
     * Maps a run result to the outcome the notification reports, or to nothing when the run is not reported.
     */
    static Optional<Outcome> reportedOutcome(@Nullable CompetencyOrchestrationResultDTO result) {
        if (result == null) {
            return Optional.of(Outcome.FAILED);
        }
        return switch (result.status()) {
            case SUCCESS -> result.appliedActions().isEmpty() ? Optional.empty() : Optional.of(Outcome.COMPLETED);
            case PARTIAL -> Optional.of(Outcome.PARTIAL);
            case FAILED -> Optional.of(Outcome.FAILED);
            case IN_PROGRESS, NO_OP -> Optional.empty();
        };
    }

    /**
     * The course's instructors and all active administrators, each once. Administrators are not course members, so
     * they are looked up separately and only receive the e-mail if they opted in for this course.
     */
    private List<User> findEligibleRecipients(Course course) {
        Map<Long, User> recipientsById = new LinkedHashMap<>();
        userRepository.getInstructors(course).forEach(user -> recipientsById.putIfAbsent(user.getId(), user));
        Set<String> adminLogins = userRepository.findAllActiveAdminLogins();
        if (!adminLogins.isEmpty()) {
            userRepository.findAllWithAuthoritiesByDeletedIsFalseAndLoginIn(adminLogins).forEach(user -> recipientsById.putIfAbsent(user.getId(), user));
        }
        return new ArrayList<>(recipientsById.values());
    }

    static AtlasCompetencyUpdatePayloadDTO buildPayload(Outcome outcome, int exerciseCount, List<AppliedActionDTO> appliedActions) {
        Map<AppliedActionDTO.ActionType, Integer> counts = new EnumMap<>(AppliedActionDTO.ActionType.class);
        for (AppliedActionDTO action : appliedActions) {
            if (action.type() != null) {
                counts.merge(action.type(), 1, Integer::sum);
            }
        }
        int listed = Math.min(appliedActions.size(), MAX_LISTED_CHANGES);
        List<String> paragraphs = new ArrayList<>(listed);
        for (AppliedActionDTO action : appliedActions.subList(0, listed)) {
            paragraphs.add(renderAction(action));
        }
        return new AtlasCompetencyUpdatePayloadDTO(outcome.name(), exerciseCount, appliedActions.size(), counts.getOrDefault(AppliedActionDTO.ActionType.CREATE, 0),
                counts.getOrDefault(AppliedActionDTO.ActionType.EDIT, 0), counts.getOrDefault(AppliedActionDTO.ActionType.DELETE, 0),
                counts.getOrDefault(AppliedActionDTO.ActionType.ASSIGN, 0), counts.getOrDefault(AppliedActionDTO.ActionType.UNASSIGN, 0), String.join("\n\n", paragraphs),
                appliedActions.size() - listed);
    }

    /**
     * One markdown paragraph per change: the sentence the tool recorded, which the manual result dialog shows as well,
     * and the model's justification in italics on the next line.
     */
    private static String renderAction(AppliedActionDTO action) {
        String detail = action.detail() == null || action.detail().isBlank() ? action.type() + " " + action.competencyTitle() : action.detail();
        String paragraph = escapeMarkdown(detail);
        if (action.justification() != null && !action.justification().isBlank()) {
            paragraph += "\n*" + escapeMarkdown(action.justification()) + "*";
        }
        return paragraph;
    }

    /**
     * Collapses whitespace so the text stays within its paragraph, and backslash-escapes all ASCII punctuation so that
     * competency titles and model output render literally instead of as markdown.
     */
    static String escapeMarkdown(String text) {
        String singleLine = text.replaceAll("\\s+", " ").strip();
        StringBuilder escaped = new StringBuilder(singleLine.length() + 16);
        for (char character : singleLine.toCharArray()) {
            if (MARKDOWN_PUNCTUATION.indexOf(character) >= 0) {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }
}
