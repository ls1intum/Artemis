package de.tum.cit.aet.artemis.hyperion.service.variants;

import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Capability interface: deterministic + semantic verification of a transformed variant during VERIFYING.
 * Gates that don't depend on each other's results run concurrently; a gate that needs another gate's outcome to
 * be trustworthy (e.g. test-case data that only settles once a build completes) waits for it. The model can
 * never talk its way past any gate.
 */
public interface VariantVerifier {

    /**
     * Runs the full verification chain for the variant.
     *
     * @param variant the transformed variant
     * @param plan    the ChangePlan whose invariants the semantic gate checks
     * @param job     the running job — LLM-backed gates (consistency check, quiz critique) attribute their
     *                    token usage to it
     * @param toolset the SAME toolset instance the just-finished agent round used; unused by verifiers with
     *                    nothing to reuse from it
     * @return structured report; findings feed the agent loop as the repair signal, or the warnings list on
     *         DRAFT_WITH_WARNINGS (never silent success)
     */
    VerificationReport verify(Exercise variant, ChangePlan plan, VariantJob job, VariantToolset toolset);
}
