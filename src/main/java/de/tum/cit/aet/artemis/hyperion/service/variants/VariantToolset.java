package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.Nullable;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

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
     * @param callbacks the raw callbacks from {@code MethodToolCallbackProvider}
     * @param stats     mutable accumulator, mutated in place as calls happen; a {@link ConcurrentHashMap} because
     *                      Spring AI may invoke callbacks concurrently for a multi-call model turn
     * @return the same callbacks, each wrapped with timing
     */
    static List<ToolCallback> withTiming(ToolCallback[] callbacks, ConcurrentHashMap<String, VariantJob.CallStat> stats) {
        List<ToolCallback> wrapped = new ArrayList<>();
        for (ToolCallback callback : callbacks) {
            wrapped.add(new ToolCallback() {

                @Override
                public ToolDefinition getToolDefinition() {
                    return callback.getToolDefinition();
                }

                @Override
                public ToolMetadata getToolMetadata() {
                    return callback.getToolMetadata();
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
     * @return the commit hash of the LAST build this round that reached its repository-type target (solution
     *         100%, template 0%-with-tests), keyed by repository type — empty when the toolset has nothing to
     *         reuse (no build ran this round, quiz has no builds, or the last build for a repository failed).
     *         The deterministic VERIFYING gate compares this against the repository's current commit: an exact
     *         match means the agent's OWN build already proved the target on the exact commit being verified, so
     *         the gate can skip re-triggering a build that would just reproduce the same result. A test-repo
     *         change invalidates the solution/template entries here (build-dependency constraint), so a stale
     *         match is never possible.
     */
    default Map<RepositoryType, String> lastGreenBuildCommits() {
        return Map.of();
    }

    /**
     * Persists any work the round left unpersisted, called by the loop runner at the end of every round
     * (after the model's final response, before the round result is reported). For programming this
     * commits and pushes uncommitted working-tree edits: only runBuild commits during the round, so a round
     * that ends without a final runBuild would otherwise silently drop its edits — and verification, which
     * builds the last PUSHED commit, would trivially pass on the unchanged provision commit. Default: no-op
     * for toolsets whose tools persist immediately (quiz).
     */
    default void flushPendingChanges() {
    }
}
