package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.critic;

/**
 * One complete implementation difference proposed as a semantic hole and proven by the environment to survive the current graded suite.
 *
 * @param ruleId                 exact approved-specification rule the mutant violates
 * @param solutionPath           repository-relative path of the one solution file the mutant replaces
 * @param originalSolutionSource pristine source the reviewer saw, retained so a later probe cannot apply the mutant to a different implementation
 * @param mutantSource           complete replacement source for {@code solutionPath}
 * @param counterexample         focused test that distinguishes the pristine solution from the mutant
 */
public record SemanticMutant(String ruleId, String solutionPath, String originalSolutionSource, String mutantSource, ContractWitness counterexample) {
}
