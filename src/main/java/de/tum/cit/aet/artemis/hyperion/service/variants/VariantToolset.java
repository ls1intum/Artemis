package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.util.List;

import jakarta.annotation.Nullable;

import org.springframework.ai.tool.ToolCallback;

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
