package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

import org.jspecify.annotations.Nullable;

/**
 * One complete implementation difference proposed as a semantic hole. It remains a hypothesis until the environment evaluates it.
 *
 * @param ruleId                 exact approved-specification rule the mutant violates
 * @param solutionPath           repository-relative path of the one solution file the mutant replaces
 * @param originalSolutionSource pristine source the reviewer saw, retained so a later probe cannot apply the mutant to a different implementation
 * @param mutantSource           complete replacement source for {@code solutionPath}
 * @param counterexample         focused test that distinguishes the pristine solution from the mutant
 * @param reviewTarget           exact text-only review hypothesis this mutant implements, or {@code null} when it was authored independently
 */
public record SemanticMutant(String ruleId, String solutionPath, String originalSolutionSource, String mutantSource, ContractWitness counterexample,
        SpecFidelityReport.@Nullable Finding reviewTarget) {

    /** Concise run-local history used only to keep later mutation samples novel; it carries no contract authority or executable-evidence claim. */
    public record Exclusion(String ruleId, String solutionPath, String misconception) {
    }

    public SemanticMutant(String ruleId, String solutionPath, String originalSolutionSource, String mutantSource, ContractWitness counterexample) {
        this(ruleId, solutionPath, originalSolutionSource, mutantSource, counterexample, null);
    }

    public Exclusion exclusion() {
        return new Exclusion(ruleId, solutionPath, counterexample.wrongBehavior());
    }
}
