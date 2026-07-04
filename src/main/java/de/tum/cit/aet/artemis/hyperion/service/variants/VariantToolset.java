package de.tum.cit.aet.artemis.hyperion.service.variants;

import java.util.List;

import jakarta.annotation.Nullable;

import org.springframework.ai.tool.ToolCallback;

/**
 * One agent round's toolset plus the per-round state the pipeline needs back after the round
 * (plan Sections 2.5 and 3): the agent's own finish summary and — for programming — whether the round
 * touched the test repository (build-dependency constraint, Section 3). Created per round by
 * {@link VariantToolsetFactory#createTools}; instances are stateful and must not be reused across rounds.
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
     *         invalid then and both builds must be re-verified (plan Section 3)
     */
    default boolean touchedTestRepo() {
        return false;
    }
}
