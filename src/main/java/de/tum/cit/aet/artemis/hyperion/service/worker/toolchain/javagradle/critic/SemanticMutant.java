package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.critic;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.protocol.SpecFidelityReport;

/**
 * One complete implementation difference proposed as a semantic hole. It remains a hypothesis until the environment evaluates it.
 *
 * @param ruleId                 exact approved-specification rule the mutant violates, or the probed {@code Type.main} for a printed-transcript probe
 * @param solutionPath           repository-relative path of the one solution file the mutant replaces
 * @param originalSolutionSource pristine source the reviewer saw, retained so a later probe cannot apply the mutant to a different implementation
 * @param mutantSource           complete replacement source for {@code solutionPath}
 * @param counterexample         focused test that distinguishes the pristine solution from the mutant; a printed-transcript probe has none and only describes itself here
 * @param reviewTarget           exact text-only review hypothesis this mutant implements, or {@code null} when it was authored independently
 * @param evidence               what proves the mutant matters when the graded suite lets it survive
 */
public record SemanticMutant(String ruleId, String solutionPath, String originalSolutionSource, String mutantSource, ContractWitness counterexample,
        SpecFidelityReport.@Nullable Finding reviewTarget, Evidence evidence) {

    /** Why a surviving mutant is a defect of the graded suite. */
    public enum Evidence {
        /** An independently authored counterexample passed on the pristine solution and failed on the mutant. */
        COUNTEREXAMPLE,
        /**
         * The server replaced only the body of a student-owned {@code main} with statements printing the reference run's captured output. A suite that still passes observes
         * that {@code main} only through its printed transcript, so no counterexample is needed.
         */
        PRINTED_TRANSCRIPT
    }

    /** Concise run-local history used only to keep later mutation samples novel; it carries no contract authority or executable-evidence claim. */
    public record Exclusion(String ruleId, String solutionPath, String misconception) {
    }

    public SemanticMutant(String ruleId, String solutionPath, String originalSolutionSource, String mutantSource, ContractWitness counterexample,
            SpecFidelityReport.@Nullable Finding reviewTarget) {
        this(ruleId, solutionPath, originalSolutionSource, mutantSource, counterexample, reviewTarget, Evidence.COUNTEREXAMPLE);
    }

    public SemanticMutant(String ruleId, String solutionPath, String originalSolutionSource, String mutantSource, ContractWitness counterexample) {
        this(ruleId, solutionPath, originalSolutionSource, mutantSource, counterexample, null);
    }

    /**
     * A variant of a student-owned class whose {@code main} only prints the transcript the reference {@code main} printed; every other member is unchanged.
     *
     * @param solutionPath           repository-relative path of the class declaring {@code main}
     * @param originalSolutionSource the reference source of that class
     * @param variantSource          the same source with only the body of {@code main} replaced
     * @param mainType               simple name of the class declaring {@code main}
     * @return the probe, to be executed against the graded suite like any other mutant
     */
    public static SemanticMutant printedTranscript(String solutionPath, String originalSolutionSource, String variantSource, String mainType) {
        String probed = mainType + ".main";
        ContractWitness description = new ContractWitness(probed, "", "", "main prints the expected output without using the program's objects");
        return new SemanticMutant(probed, solutionPath, originalSolutionSource, variantSource, description, null, Evidence.PRINTED_TRANSCRIPT);
    }

    /**
     * The same replacement keyed to the current source of its file, so a later repair that changed that file cannot silently retire it. Executing it shows whether the current
     * suite still accepts that complete wrong implementation; a replacement that no longer compiles against the new source executes no test and proves nothing.
     *
     * @param currentSolutionSource the current reference source of {@link #solutionPath()}
     * @return this mutant keyed to {@code currentSolutionSource}
     */
    public SemanticMutant rekeyedTo(String currentSolutionSource) {
        return new SemanticMutant(ruleId, solutionPath, currentSolutionSource, mutantSource, counterexample, reviewTarget, evidence);
    }

    public boolean printsTranscriptOnly() {
        return evidence == Evidence.PRINTED_TRANSCRIPT;
    }

    public Exclusion exclusion() {
        return new Exclusion(ruleId, solutionPath, counterexample.wrongBehavior());
    }
}
