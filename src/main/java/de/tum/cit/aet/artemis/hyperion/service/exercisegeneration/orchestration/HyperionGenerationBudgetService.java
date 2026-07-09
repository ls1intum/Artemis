package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.time.Duration;
import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.repository.LLMTokenUsageTraceRepository;
import de.tum.cit.aet.artemis.core.exception.TooManyRequestsAlertException;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;

/**
 * Admission-control token budgets for Hyperion generation. Limits are disabled when configured as {@code 0}, so installations can opt in per deployment.
 */
@Lazy
@Service
@Conditional(HyperionExerciseGenerationEnabled.class)
public class HyperionGenerationBudgetService {

    private static final String ENTITY_NAME = "hyperionExerciseGeneration";

    private final LLMTokenUsageTraceRepository tokenUsageTraceRepository;

    private final Duration budgetWindow;

    private final long maxTokensPerUser;

    private final long maxTokensPerCourse;

    private final long maxTokensGlobal;

    public HyperionGenerationBudgetService(LLMTokenUsageTraceRepository tokenUsageTraceRepository,
            @Value("${artemis.hyperion.agent.token-budget-window:PT24H}") Duration budgetWindow,
            @Value("${artemis.hyperion.agent.admission-max-tokens-per-user:${artemis.hyperion.agent.max-tokens-per-user:0}}") long maxTokensPerUser,
            @Value("${artemis.hyperion.agent.admission-max-tokens-per-course:${artemis.hyperion.agent.max-tokens-per-course:0}}") long maxTokensPerCourse,
            @Value("${artemis.hyperion.agent.admission-max-tokens-global:${artemis.hyperion.agent.max-tokens-global:0}}") long maxTokensGlobal) {
        this.tokenUsageTraceRepository = tokenUsageTraceRepository;
        this.budgetWindow = budgetWindow;
        this.maxTokensPerUser = maxTokensPerUser;
        this.maxTokensPerCourse = maxTokensPerCourse;
        this.maxTokensGlobal = maxTokensGlobal;
    }

    /**
     * Fails fast before starting a new expensive generation run if any configured rolling token budget is already exhausted.
     */
    public void assertWithinBudgets(@Nullable Long userId, @Nullable Long courseId) {
        if (maxTokensPerUser <= 0 && maxTokensPerCourse <= 0 && maxTokensGlobal <= 0) {
            return;
        }
        ZonedDateTime since = ZonedDateTime.now().minus(budgetWindow);
        if (maxTokensPerUser > 0 && userId != null
                && tokenUsageTraceRepository.sumTokensSinceForUser(LLMServiceType.HYPERION, GenerationJobService.GENERATION_PIPELINE_ID, userId, since) >= maxTokensPerUser) {
            throw budgetExceeded("Your Hyperion generation token budget is currently exhausted. Please try again later.");
        }
        if (maxTokensPerCourse > 0 && courseId != null
                && tokenUsageTraceRepository.sumTokensSinceForCourse(LLMServiceType.HYPERION, GenerationJobService.GENERATION_PIPELINE_ID, courseId, since) >= maxTokensPerCourse) {
            throw budgetExceeded("The course Hyperion generation token budget is currently exhausted. Please try again later.");
        }
        if (maxTokensGlobal > 0 && tokenUsageTraceRepository.sumTokensSince(LLMServiceType.HYPERION, GenerationJobService.GENERATION_PIPELINE_ID, since) >= maxTokensGlobal) {
            throw budgetExceeded("The global Hyperion generation token budget is currently exhausted. Please try again later.");
        }
    }

    private static TooManyRequestsAlertException budgetExceeded(String message) {
        return new TooManyRequestsAlertException(message, ENTITY_NAME, "generationTokenBudgetExceeded");
    }
}
