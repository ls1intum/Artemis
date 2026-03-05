package de.tum.cit.aet.artemis.iris.service;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisRateLimitConfiguration;
import de.tum.cit.aet.artemis.iris.exception.IrisRateLimitExceededException;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

/**
 * Service for the rate limit of the iris chatbot.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisRateLimitService {

    private final IrisMessageRepository irisMessageRepository;

    private final IrisSettingsService irisSettingsService;

    public IrisRateLimitService(IrisMessageRepository irisMessageRepository, IrisSettingsService irisSettingsService) {
        this.irisMessageRepository = irisMessageRepository;
        this.irisSettingsService = irisSettingsService;
    }

    /**
     * Get the rate limit information for the given user. Uses application-level defaults.
     *
     * @param user the user
     * @return the rate limit information
     */
    // TODO: Nachschauen, ob vorher auch nicht genutzt
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
        // Only count messages if rate limiting is active (both not unlimited)
        // Note: requestsLimit=0 means blocking, so we still need to check even though limit is 0
        if (requestsLimit != -1 && timeframeHours != -1) {
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
    // TODO: Nachschauen, ob vorher auch nicht genutzt
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
        return irisSettingsService.getCourseSettingsWithRateLimit(courseId).effectiveRateLimit();
    }

    private Optional<Long> resolveCourseId(IrisChatSession session) {
        // IrisChatSession always carries courseId; 0 means not set (e.g. tutor suggestions)
        long courseId = session.getCourseId();
        return courseId > 0 ? Optional.of(courseId) : Optional.empty();
    }

    /**
     * Resolves the requests limit from the configuration.
     * <p>
     * - null = unlimited (returns -1)
     * - 0 = blocking (returns 0, meaning no requests allowed)
     * - positive = explicit limit
     *
     * @param configuration the rate limit configuration
     * @return the resolved requests limit (-1 = unlimited, 0 = blocking, positive = limit)
     */
    private int resolveRequestsLimit(IrisRateLimitConfiguration configuration) {
        var requests = configuration.requests();
        if (requests == null) {
            return -1; // unlimited
        }
        // 0 means blocking (no requests allowed), positive means explicit limit
        return Math.max(0, requests);
    }

    /**
     * Resolves the timeframe from the configuration.
     * <p>
     * - null = unlimited (returns -1)
     * - positive = explicit timeframe in hours
     *
     * @param configuration the rate limit configuration
     * @return the resolved timeframe (-1 = unlimited, positive = hours)
     */
    private int resolveTimeframe(IrisRateLimitConfiguration configuration) {
        var timeframe = configuration.timeframeHours();
        if (timeframe == null) {
            return -1; // unlimited
        }
        // Ensure at least 1 hour if set
        return Math.max(1, timeframe);
    }

    /**
     * Contains information about the rate limit of a user.
     *
     * @param currentMessageCount     the current rate limit
     * @param rateLimit               the max rate limit (-1 = unlimited, 0 = blocking, positive = limit)
     * @param rateLimitTimeframeHours timeframe in hours (-1 = unlimited, positive = hours)
     */
    public record IrisRateLimitInformation(int currentMessageCount, int rateLimit, int rateLimitTimeframeHours) {

        /**
         * Checks if the rate limit is exceeded.
         * <p>
         * The limit is exceeded if:
         * - rateLimit is 0 (blocking - no requests allowed), OR
         * - rateLimit is positive AND currentMessageCount >= rateLimit
         * <p>
         * If rateLimit is -1 (unlimited), the limit is never exceeded.
         *
         * @return true if the rate limit is exceeded, false otherwise
         */
        public boolean isRateLimitExceeded() {
            if (rateLimit == -1) {
                return false; // unlimited
            }
            if (rateLimit == 0) {
                return true; // blocking - always exceeded
            }
            return currentMessageCount >= rateLimit;
        }
    }
}
