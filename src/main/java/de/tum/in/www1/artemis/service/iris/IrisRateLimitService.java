package de.tum.in.www1.artemis.service.iris;

import java.time.ZonedDateTime;
import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.iris.IrisMessageRepository;
import de.tum.in.www1.artemis.service.iris.exception.IrisRateLimitExceededException;

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
    public IrisRateLimitInformation getRateLimit(User user) {
        var globalSettings = irisSettingsService.getGlobalSettings();
        var irisChatSettings = globalSettings.getIrisChatSettings();
        var start = ZonedDateTime.now().minusHours(3);
        var end = ZonedDateTime.now();
        var currentRateLimit = irisMessageRepository.countLlmResponsesOfUserWithinTimeframe(user.getId(), start, end);
        var maxRateLimit = Objects.requireNonNullElse(irisChatSettings.getRateLimit(), -1);

        return new IrisRateLimitInformation(currentRateLimit, maxRateLimit);
    }

    /**
     * Checks if the rate limit of the given user is exceeded.
     * If it is exceeded, an {@link IrisRateLimitExceededException} is thrown.
     * See {@link #getRateLimit(User)} and {@link IrisRateLimitInformation#isRateLimitExceeded()} for more information.
     *
     * @param user the user
     * @throws IrisRateLimitExceededException if the rate limit is exceeded
     */
    public void checkRateLimitElseThrow(User user) {
        var rateLimit = getRateLimit(user);
        if (rateLimit.isRateLimitExceeded()) {
            throw new IrisRateLimitExceededException(rateLimit);
        }
    }

    /**
     * Contains information about the rate limit of a user.
     *
     * @param currentRateLimit the current rate limit
     * @param maxRateLimit     the max rate limit
     */
    public record IrisRateLimitInformation(int currentRateLimit, int maxRateLimit) {

        /**
         * Checks if the rate limit is exceeded.
         * It is exceeded if the maxRateLimit is set and the currentRateLimit is greater or equal to the maxRateLimit.
         *
         * @return true if the rate limit is exceeded, false otherwise
         */
        public boolean isRateLimitExceeded() {
            return maxRateLimit != -1 && currentRateLimit >= maxRateLimit;
        }
    }
}
