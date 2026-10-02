package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * One agent round's toolset plus the per-round state the pipeline needs back after the round: the agent's own
 * finish summary and — for programming — whether the round touched the test repository (build-dependency
 * constraint). Created per round by {@link VariantToolsetFactory#createTools}; instances are stateful and must
 * not be reused across rounds.
 */
public interface VariantToolset {

    /**
     * @return the tool callbacks to register on the ChatClient call for this round
     */
    List<ToolCallback> toolCallbacks();

    /**
     * @return per-tool-call baseline telemetry collected during this round (tool name -> call count/total wall-
     *         clock ms), or empty when the toolset does not instrument its callbacks
     */
    default Map<String, VariantJob.CallStat> toolCallStats() {
        return Map.of();
    }

    /**
     * Wraps every callback with call-count/wall-clock timing, accumulating into {@code stats} (tool name ->
     * {@link VariantJob.CallStat}). Shared by every toolset implementation so per-tool-call telemetry doesn't
     * have to be threaded through each individual tool method — it wraps at the single choke point every tool
     * call already passes through (the callback Spring AI actually invokes), independent of what the tool itself
     * does internally.
     *
     * The same wrapper carries the round's hard stop: Spring AI's internal tool loop only ends when no tool is
     * called or a called tool is {@code returnDirect}, so a toolset's own "budget exhausted, call finish"
     * notice is advisory — a model that keeps calling tools instead of {@code finish} would loop on. Reporting
     * {@code returnDirect} for every tool once {@code roundOver} holds ends that loop deterministically, and
     * the metadata is read per call, so the toolset can flip the condition mid-round.
     *
     * @param callbacks the raw callbacks from {@code MethodToolCallbackProvider}
     * @param stats     mutable accumulator, mutated in place as calls happen; a {@link ConcurrentHashMap} because
     *                      Spring AI may invoke callbacks concurrently for a multi-call model turn
     * @param roundOver the toolset's "this round must end now" condition (hard tool-call stop, cancellation)
     * @return the same callbacks, each wrapped with timing and the hard stop
     */
    static List<ToolCallback> withTiming(ToolCallback[] callbacks, ConcurrentHashMap<String, VariantJob.CallStat> stats, BooleanSupplier roundOver) {
        List<ToolCallback> wrapped = new ArrayList<>();
        for (ToolCallback callback : callbacks) {
            wrapped.add(new ToolCallback() {

                @Override
                public ToolDefinition getToolDefinition() {
                    return callback.getToolDefinition();
                }

                @Override
                public ToolMetadata getToolMetadata() {
                    return roundOver.getAsBoolean() ? ToolMetadata.builder().returnDirect(true).build() : callback.getToolMetadata();
                }

                @Override
                public String call(String toolInput) {
                    long start = System.nanoTime();
                    try {
                        return callback.call(toolInput);
                    }
                    finally {
                        record(stats, callback.getToolDefinition().name(), start);
                    }
                }

                @Override
                public String call(String toolInput, ToolContext toolContext) {
                    long start = System.nanoTime();
                    try {
                        return callback.call(toolInput, toolContext);
                    }
                    finally {
                        record(stats, callback.getToolDefinition().name(), start);
                    }
                }
            });
        }
        return wrapped;
    }

    private static void record(ConcurrentHashMap<String, VariantJob.CallStat> stats, String toolName, long startNanos) {
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
        stats.compute(toolName, (name, existing) -> existing == null ? new VariantJob.CallStat(1, elapsedMillis)
                : new VariantJob.CallStat(existing.count() + 1, existing.totalMillis() + elapsedMillis));
    }

    /**
     * @return the summary the agent passed to its finish tool, or {@code null} when it never called finish
     */
    @Nullable
    String finishSummary();

    /**
     * @return true when the round edited (or rebuilt) the test repository — prior green build evidence is
     *         invalid then and both builds must be re-verified
     */
    default boolean touchedTestRepo() {
        return false;
    }

    /**
     * Prefetched repository context to seed the round's OPENING user message (performance lever A4): each
     * ChatClient call is a fresh conversation with no memory of a previous round's reads, so a round otherwise
     * starts blind and spends its first several tool calls just discovering what it's working with — on every
     * repair round too. Bounded by an internal budget.
     *
     * @param plan the round's binding ChangePlan, so an implementation can target the prefetch at what the plan
     *                 actually intends to change instead of dumping the whole repository
     * @return prefetched context to append to the opening user message, or empty when there is nothing to
     *         prefetch (quiz has no repositories) or the plan gives no reliable signal of which files matter
     */
    default String prefetchContext(ChangePlan plan) {
        return "";
    }

    /**
     * Persists any work the round left unpersisted, called by the loop runner at the end of every round
     * (after the model's final response, before the round result is reported). For programming this commits
     * and pushes uncommitted working-tree edits: nothing else in the round commits, so without this call a
     * round would otherwise silently drop its edits — and verification, which builds the last PUSHED commit,
     * would trivially pass on the unchanged provision commit. Default: no-op for toolsets whose tools persist
     * immediately (quiz).
     */
    default void flushPendingChanges() {
    }
}
