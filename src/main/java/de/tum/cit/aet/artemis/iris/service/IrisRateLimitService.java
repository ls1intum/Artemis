package de.tum.cit.aet.artemis.iris.service;

import java.time.ZonedDateTime;
import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.iris.exception.IrisRateLimitExceededException;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

/**
 * Service for the rate limit of the iris chatbot.
 */
@Service
@Profile("iris")
public class IrisRateLimitService {

    private final IrisMessageRepository irisMessageRepository;

    private final IrisSettingsService irisSettingsService;

    public IrisRateLimitService(IrisMessageRepository irisMessageRepository, IrisSettingsService irisSettingsService) {
        this.irisMessageRepository = irisMessageRepository;
        this.irisSettingsService = irisSettingsService;
    }

    /**
     * Get the rate limit information for the given user.
     * See {@link IrisRateLimitInformation} and {@link IrisRateLimitInformation#isRateLimitExceeded()} for more information.
     *
     * @param user the user
     * @return the rate limit information
     */
    public IrisRateLimitInformation getRateLimitInformation(User user) {
        var globalSettings = irisSettingsService.getGlobalSettings();
        var irisChatSettings = globalSettings.getIrisChatSettings();
        int rateLimitTimeframeHours = Objects.requireNonNullElse(irisChatSettings.getRateLimitTimeframeHours(), 0);
        var start = ZonedDateTime.now().minusHours(rateLimitTimeframeHours);
        var end = ZonedDateTime.now();
        var currentMessageCount = irisMessageRepository.countLlmResponsesOfUserWithinTimeframe(user.getId(), start, end);
        var rateLimit = Objects.requireNonNullElse(irisChatSettings.getRateLimit(), -1);

        return new IrisRateLimitInformation(currentMessageCount, rateLimit, rateLimitTimeframeHours);
    }

    /**
     * Checks if the rate limit of the given user is exceeded.
     * If it is exceeded, an {@link IrisRateLimitExceededException} is thrown.
     * See {@link #getRateLimitInformation(User)} and {@link IrisRateLimitInformation#isRateLimitExceeded()} for more information.
     *
     * @param user the user
     * @throws IrisRateLimitExceededException if the rate limit is exceeded
     */
    public void checkRateLimitElseThrow(User user) {
        var rateLimitInfo = getRateLimitInformation(user);
        if (rateLimitInfo.isRateLimitExceeded()) {
            throw new IrisRateLimitExceededException(rateLimitInfo);
        }
    }

    /**
     * Contains information about the rate limit of a user.
     *
     * @param currentMessageCount the current rate limit
     * @param rateLimit           the max rate limit
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
