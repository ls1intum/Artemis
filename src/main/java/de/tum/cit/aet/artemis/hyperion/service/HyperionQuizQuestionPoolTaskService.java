package de.tum.cit.aet.artemis.hyperion.service;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.hyperion.config.HyperionEnabled;

/**
 * Async dispatch for quiz question pool builds. The {@code @Async} boundary lives in its own bean so the call crosses a Spring
 * proxy, which a self-invocation would bypass.
 */
@Service
@Lazy
@Conditional(HyperionEnabled.class)
public class HyperionQuizQuestionPoolTaskService {

    private final HyperionQuizQuestionPoolBuildService buildService;

    public HyperionQuizQuestionPoolTaskService(HyperionQuizQuestionPoolBuildService buildService) {
        this.buildService = buildService;
    }

    /**
     * Runs a pool build on the pool executor and releases the course's build slot when it ends, however it ends.
     *
     * @param courseId the course whose pool to build; its build slot must already be held
     * @param userId   the user the token usage is attributed to; may be {@code null}
     */
    @Async("hyperionQuizQuestionPoolTaskExecutor")
    public void runBuildAsync(long courseId, @Nullable Long userId) {
        buildService.buildAndRelease(courseId, userId);
    }
}
