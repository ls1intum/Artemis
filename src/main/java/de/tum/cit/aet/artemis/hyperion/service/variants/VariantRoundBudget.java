package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;

/**
 * One agent round's tool-call accounting, shared by every toolset: the liveness heartbeat, the cancellation
 * check, the per-round tool-call budget and the hard stop that ends a round which ignores it.
 *
 * The budget exists because Spring AI's internal tool loop has no iteration cap, and a model that keeps
 * re-reading and re-reasoning would loop indefinitely (observed with a local reasoning model: 100-message
 * conversations). Its directive is advisory though — only a {@code returnDirect} result ends that loop — so
 * {@link #roundOver()} turns true {@link #GRACE_CALLS} calls later, and {@link VariantToolset#withTiming}
 * makes every tool return directly while it holds. Cancellation ends the round the same way: nothing it
 * produces from there on is kept.
 */
class VariantRoundBudget {

    /** Calls granted past the budget before the round is stopped outright — room for the model to call finish itself. */
    private static final int GRACE_CALLS = 10;

    private final int budget;

    private final String jobId;

    private final ExerciseVariantJobService jobService;

    /** Atomic: Spring AI may invoke tool callbacks concurrently within one model turn. */
    private final AtomicInteger used = new AtomicInteger();

    private volatile boolean roundOver;

    VariantRoundBudget(int budget, String jobId, ExerciseVariantJobService jobService) {
        this.budget = budget;
        this.jobId = jobId;
        this.jobService = jobService;
    }

    /**
     * Whether the round must end with the result of the call being made. Evaluated against the calls already
     * used and the live cancel flag rather than only what {@link #stopNotice()} last saw, because Spring AI's
     * tool-calling manager reads a callback's metadata BEFORE invoking it: a flag set inside the call it is
     * deciding about would arrive one model turn too late. Sticky once true.
     *
     * @return true once the round must end — cancelled, or the budget's grace calls are used up
     */
    boolean roundOver() {
        if (!roundOver) {
            roundOver = used.get() >= budget + GRACE_CALLS || jobService.isCancelRequested(jobId);
        }
        return roundOver;
    }

    /**
     * Records one tool call and reports whether the caller must short-circuit. Short-circuit instead of
     * throwing: Spring AI returns tool exceptions to the model as ordinary tool results, so an exception cannot
     * abort the round; the pipeline performs the actual abort at the next round boundary.
     *
     * @return the directive to return to the model instead of doing the tool's work, or null to proceed
     */
    @Nullable
    String stopNotice() {
        // Every tool call is a liveness signal for the long internal agent round (see the job's staleness handling).
        jobService.heartbeat(jobId);
        if (jobService.isCancelRequested(jobId)) {
            roundOver = true;
            return "The variant generation job was CANCELLED. Do not call any more tools; the round is over and all further work will be discarded.";
        }
        int callsUsed = used.incrementAndGet();
        if (callsUsed > budget) {
            return "TOOL BUDGET EXHAUSTED for this round (" + budget + " calls). Do not call any other tool. Call finish NOW with a short summary of what you changed.";
        }
        return null;
    }
}
