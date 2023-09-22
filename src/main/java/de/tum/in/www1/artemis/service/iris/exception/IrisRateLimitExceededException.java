package de.tum.in.www1.artemis.service.iris.exception;

import java.util.Map;

import org.zalando.problem.Status;

import de.tum.in.www1.artemis.service.iris.IrisRateLimitService;

public class IrisRateLimitExceededException extends IrisException {

    public IrisRateLimitExceededException(int currentRateLimit, int maxRateLimit) {
        super("You have exceeded the rate limit of Iris", Status.TOO_MANY_REQUESTS, "Iris", "artemisApp.exerciseChatbot.errors.rateLimitExceeded",
                Map.of("currentRateLimit", currentRateLimit, "maxRateLimit", maxRateLimit));
    }

    public IrisRateLimitExceededException(IrisRateLimitService.RateLimitDTO rateLimit) {
        this(rateLimit.currentRateLimit(), rateLimit.maxRateLimit());
    }
}
