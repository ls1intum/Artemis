package de.tum.cit.aet.artemis.iris.exception;

import java.util.Map;

import org.zalando.problem.Status;

import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;

/**
 * Exception that is thrown when the rate limit of Iris is exceeded.
 * See {@link IrisRateLimitService} for more information.
 * It is mapped to the "429 Too Many Requests" HTTP status code.
 */
public class IrisRateLimitExceededException extends IrisException {

    public IrisRateLimitExceededException(int currentMessageCount, int rateLimit) {
        super("You have exceeded the rate limit of Iris", Status.TOO_MANY_REQUESTS, "Iris", "artemisApp.exerciseChatbot.errors.rateLimitExceeded",
                Map.of("currentMessageCount", currentMessageCount, "rateLimit", rateLimit));
    }

    public IrisRateLimitExceededException(IrisRateLimitService.IrisRateLimitInformation rateLimit) {
        this(rateLimit.currentMessageCount(), rateLimit.rateLimit());
    }
}
