package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

import jakarta.annotation.Nullable;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.repository.PostRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisLectureChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisProgrammingExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTextExerciseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisRateLimitConfiguration;
import de.tum.cit.aet.artemis.iris.exception.IrisRateLimitExceededException;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.lecture.api.LectureRepositoryApi;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.text.api.TextRepositoryApi;

/**
 * Service for the rate limit of the iris chatbot.
 */
@Lazy
@Service
@Profile(PROFILE_IRIS)
public class IrisRateLimitService {

    private final IrisMessageRepository irisMessageRepository;

    private final IrisSettingsService irisSettingsService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final Optional<TextRepositoryApi> textRepositoryApi;

    private final Optional<LectureRepositoryApi> lectureRepositoryApi;

    private final Optional<PostRepository> postRepository;

    public IrisRateLimitService(IrisMessageRepository irisMessageRepository, IrisSettingsService irisSettingsService, ProgrammingExerciseRepository programmingExerciseRepository,
            Optional<TextRepositoryApi> textRepositoryApi, Optional<LectureRepositoryApi> lectureRepositoryApi, Optional<PostRepository> postRepository) {
        this.irisMessageRepository = irisMessageRepository;
        this.irisSettingsService = irisSettingsService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.textRepositoryApi = textRepositoryApi;
        this.lectureRepositoryApi = lectureRepositoryApi;
        this.postRepository = postRepository;
    }

    /**
     * Get the rate limit information for the given user. Uses application-level defaults.
     *
     * @param user the user
     * @return the rate limit information
     */
    public IrisRateLimitInformation getRateLimitInformation(User user) {
        return getRateLimitInformation((Long) null, user);
    }

    /**
     * Get the rate limit information for a user within the context of a course.
     *
     * @param courseId optional course id providing overrides
     * @param user     the user
     * @return resolved rate limit information
     */
    public IrisRateLimitInformation getRateLimitInformation(@Nullable Long courseId, User user) {
        var configuration = resolveRateLimitConfiguration(courseId);
        int requestsLimit = resolveRequestsLimit(configuration);
        int timeframeHours = resolveTimeframe(configuration);

        int currentMessageCount = 0;
        if (requestsLimit != -1 && timeframeHours > 0) {
            var end = ZonedDateTime.now();
            var start = end.minusHours(timeframeHours);
            currentMessageCount = irisMessageRepository.countLlmResponsesOfUserWithinTimeframe(user.getId(), start, end);
        }

        return new IrisRateLimitInformation(currentMessageCount, requestsLimit, timeframeHours);
    }

    /**
     * Get the rate limit information for the given user and session, honoring the session's course context if available.
     *
     * @param session the iris session (used to determine course context)
     * @param user    the user
     * @return resolved rate limit information
     */
    public IrisRateLimitInformation getRateLimitInformation(IrisChatSession session, User user) {
        return getRateLimitInformation(resolveCourseId(session).orElse(null), user);
    }

    /**
     * Checks if the rate limit of the given user is exceeded using application defaults.
     *
     * @param user the user
     */
    public void checkRateLimitElseThrow(User user) {
        checkRateLimitElseThrow((Long) null, user);
    }

    /**
     * Checks if the rate limit of the given user is exceeded within the context of the given course id.
     *
     * @param courseId optional course id providing overrides
     * @param user     the user
     */
    public void checkRateLimitElseThrow(@Nullable Long courseId, User user) {
        var rateLimitInfo = getRateLimitInformation(courseId, user);
        if (rateLimitInfo.isRateLimitExceeded()) {
            throw new IrisRateLimitExceededException(rateLimitInfo);
        }
    }

    /**
     * Checks if the rate limit of the given user is exceeded in the context of the provided session.
     *
     * @param session the iris session
     * @param user    the user
     */
    public void checkRateLimitElseThrow(IrisChatSession session, User user) {
        checkRateLimitElseThrow(resolveCourseId(session).orElse(null), user);
    }

    private IrisRateLimitConfiguration resolveRateLimitConfiguration(@Nullable Long courseId) {
        if (courseId == null) {
            return irisSettingsService.getApplicationRateLimitDefaults();
        }
        return irisSettingsService.getCourseSettingsDTO(courseId).effectiveRateLimit();
    }

    private Optional<Long> resolveCourseId(IrisChatSession session) {
        if (session instanceof IrisCourseChatSession courseChatSession) {
            return Optional.of(courseChatSession.getCourseId());
        }
        if (session instanceof IrisProgrammingExerciseChatSession exerciseChatSession) {
            return programmingExerciseRepository.findById(exerciseChatSession.getExerciseId()).map(exercise -> exercise.getCourseViaExerciseGroupOrCourseMember())
                    .map(this::courseIdOrNull).filter(Objects::nonNull);
        }
        if (session instanceof IrisTextExerciseChatSession textChatSession) {
            return textRepositoryApi.flatMap(api -> Optional.ofNullable(api.findByIdElseThrow(textChatSession.getExerciseId()).getCourseViaExerciseGroupOrCourseMember()))
                    .map(this::courseIdOrNull).filter(Objects::nonNull);
        }
        if (session instanceof IrisLectureChatSession lectureChatSession) {
            return lectureRepositoryApi.flatMap(api -> Optional.ofNullable(api.findByIdElseThrow(lectureChatSession.getLectureId()).getCourse())).map(this::courseIdOrNull)
                    .filter(Objects::nonNull);
        }
        if (session instanceof IrisTutorSuggestionSession tutorSuggestionSession) {
            return postRepository.flatMap(repo -> repo.findById(tutorSuggestionSession.getPostId())).map(post -> post.getCoursePostingBelongsTo()).map(this::courseIdOrNull)
                    .filter(Objects::nonNull);
        }
        return Optional.empty();
    }

    private Long courseIdOrNull(@Nullable Course course) {
        return course != null ? course.getId() : null;
    }

    private int resolveRequestsLimit(IrisRateLimitConfiguration configuration) {
        var requests = configuration.requests();
        if (requests == null || requests <= 0) {
            return -1;
        }
        return requests;
    }

    private int resolveTimeframe(IrisRateLimitConfiguration configuration) {
        var timeframe = configuration.timeframeHours();
        if (timeframe == null || timeframe <= 0) {
            return 0;
        }
        return timeframe;
    }

    /**
     * Contains information about the rate limit of a user.
     *
     * @param currentMessageCount     the current rate limit
     * @param rateLimit               the max rate limit (-1 = unlimited)
     * @param rateLimitTimeframeHours timeframe in hours (0 = unlimited)
     */
    public record IrisRateLimitInformation(int currentMessageCount, int rateLimit, int rateLimitTimeframeHours) {

        /**
         * Checks if the rate limit is exceeded.
         * It is exceeded if the rateLimit is set and the currentMessageCount is greater or equal to the rateLimit.
         *
         * @return true if the rate limit is exceeded, false otherwise
         */
        public boolean isRateLimitExceeded() {
            return rateLimit != -1 && currentMessageCount >= rateLimit;
        }
    }
}
