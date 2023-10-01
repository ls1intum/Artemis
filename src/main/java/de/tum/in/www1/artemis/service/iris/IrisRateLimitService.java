package de.tum.in.www1.artemis.service.iris;

import java.time.ZonedDateTime;

import org.springframework.beans.factory.annotation.Value;
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

    @Value("${artemis.iris.rate-limit:5}")
    private int rateLimit;

    @Value("${artemis.iris.rate-limit-timeframe-hours:24}")
    private int rateLimitTimeframeHours;

    public IrisRateLimitService(IrisMessageRepository irisMessageRepository) {
        this.irisMessageRepository = irisMessageRepository;
    }

    /**
     * Get the rate limit information for the given user.
     * See {@link IrisRateLimitInformation} and {@link IrisRateLimitInformation#isRateLimitExceeded()} for more information.
     *
     * @param user the user
     * @return the rate limit information
     */
    public IrisRateLimitInformation getRateLimitInformation(User user) {
        var start = ZonedDateTime.now().minusHours(rateLimitTimeframeHours);
        var end = ZonedDateTime.now();
        var currentMessageCount = irisMessageRepository.countLlmResponsesOfUserWithinTimeframe(user.getId(), start, end);

        return new IrisRateLimitInformation(currentMessageCount, rateLimit);
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
    public record IrisRateLimitInformation(int currentMessageCount, int rateLimit) {

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
