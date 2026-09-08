package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;

import org.springframework.ai.chat.model.ChatResponse;

import de.tum.cit.aet.artemis.admin.domain.LLMRequest;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationLiveUsageDTO;

/**
 * What one run has spent so far, accumulated on the node executing it.
 * <p>
 * It lives here rather than in the run's distributed usage map because the agent emits an event per turn plus one before every provider call, and stamping each of those from the
 * cluster would turn a progress line into a remote read. Both inputs are objects the run already has to look at for other reasons — the {@link LLMRequest} its recorded usage was
 * built from, and the {@link ChatResponse} its budget guard weighs — so no figure here is a second opinion about what a call cost.
 * <p>
 * The billable total is the guard's own running total, not a parallel count: {@link #addBillableTokens} is what the guard compares against the budget.
 */
final class GenerationLiveUsage {

    private final long tokenBudget;

    private final double cachedInputTokenWeight;

    private final AtomicLong inputTokens = new AtomicLong();

    private final AtomicLong outputTokens = new AtomicLong();

    private final AtomicLong cachedInputTokens = new AtomicLong();

    private final AtomicLong billableTokens = new AtomicLong();

    private final AtomicLong modelCalls = new AtomicLong();

    private final DoubleAdder estimatedCostEur = new DoubleAdder();

    private final AtomicBoolean estimatedCostComplete = new AtomicBoolean(true);

    GenerationLiveUsage(long tokenBudget, double cachedInputTokenWeight) {
        this.tokenBudget = tokenBudget;
        this.cachedInputTokenWeight = cachedInputTokenWeight;
    }

    /**
     * Adds one provider response whose usage was recorded.
     *
     * @param request the recorded request, carrying the token split and the prices resolved for its model
     */
    void recordAccountedRequest(LLMRequest request) {
        inputTokens.addAndGet(request.numInputTokens());
        outputTokens.addAndGet(request.numOutputTokens());
        cachedInputTokens.addAndGet(request.numCachedInputTokens() == null ? 0 : request.numCachedInputTokens());
        modelCalls.incrementAndGet();
        estimatedCostEur.add(LLMTokenUsageService.estimatedCostEur(request));
        if (!request.costEstimateComplete()) {
            // Absorbing: an unpriced model makes every later total a lower bound, and reporting it as a price would claim the run cost less than it did.
            estimatedCostComplete.set(false);
        }
    }

    /**
     * Adds what one provider response costs the token budget.
     *
     * @param response the provider response, may be null
     * @return the run's billable total including this response
     */
    long addBillableTokens(ChatResponse response) {
        return billableTokens.addAndGet(LLMTokenUsageService.billableTokens(response, cachedInputTokenWeight));
    }

    /**
     * @return the run's spend up to this moment, with the cost omitted unless every response counted so far had a configured price
     */
    ExerciseGenerationLiveUsageDTO snapshot() {
        boolean costComplete = estimatedCostComplete.get();
        return new ExerciseGenerationLiveUsageDTO(inputTokens.get(), outputTokens.get(), cachedInputTokens.get(), billableTokens.get(), tokenBudget, modelCalls.get(),
                costComplete ? estimatedCostEur.sum() : null, costComplete);
    }
}
