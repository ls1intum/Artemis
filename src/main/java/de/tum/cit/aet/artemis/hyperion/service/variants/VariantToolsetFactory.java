package de.tum.cit.aet.artemis.hyperion.service.variants;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Capability interface: builds the type-specific toolset the agent loop uses during TRANSFORMING/REPAIRING.
 * The returned {@link VariantToolset} is stateful per round: besides the Spring AI tool callbacks it carries
 * back the agent's finish summary and the touched-test-repo flag.
 */
public interface VariantToolsetFactory {

    /**
     * Creates a fresh toolset bound to the given (already provisioned) variant exercise. Must be called once
     * per agent round — the toolset accumulates round state (checkouts, build results, finish summary).
     *
     * @param variant the provisioned variant exercise the tools operate on
     * @param job     the running job — tools observe job cancellation between calls and attribute telemetry
     * @return the toolset for one agent round
     */
    VariantToolset createTools(Exercise variant, VariantJob job);
}
