package de.tum.in.www1.artemis.service.iris;

import java.time.ZonedDateTime;
import java.util.Objects;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.iris.IrisMessageRepository;

@Service
@Profile("iris")
public class IrisRateLimitService {

    private final IrisMessageRepository irisMessageRepository;

    private final IrisSettingsService irisSettingsService;

    public IrisRateLimitService(IrisMessageRepository irisMessageRepository, IrisSettingsService irisSettingsService) {
        this.irisMessageRepository = irisMessageRepository;
        this.irisSettingsService = irisSettingsService;
    }

    public RateLimitDTO getRateLimit(User user) {
        var globalSettings = irisSettingsService.getGlobalSettings();
        var irisChatSettings = globalSettings.getIrisChatSettings();
        var start = ZonedDateTime.now().minusHours(3);
        var end = ZonedDateTime.now();
        var currentRateLimit = irisMessageRepository.countLlmResponsesOfUserWithinTimeframe(user.getId(), start, end);
        var maxRateLimit = Objects.requireNonNullElse(irisChatSettings.getRateLimit(), -1);

        return new RateLimitDTO(currentRateLimit, maxRateLimit);
    }

    /**
     * DTO for the rate limit.
     *
     * @param currentRateLimit the current rate limit
     * @param maxRateLimit     the max rate limit
     */
    public record RateLimitDTO(int currentRateLimit, int maxRateLimit) {

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
