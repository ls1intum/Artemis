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
        PRINTED_TRANSCRIPT,
        /** The server removed the bodies of simple learner-owned JUnit tests; a passing graded suite does not assess their assertions. */
        EMPTY_STUDENT_TESTS,
        /** Learner test actions run, but their assertion failures cannot reach the grader. */
        NON_FAILING_STUDENT_TESTS,
        /** All assigned learner tests fail unconditionally; no weighted criterion may reward them. */
        ALWAYS_FAILING_STUDENT_TESTS
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

    /** A server-authored variant with the learner's JUnit test bodies empty and all declarations unchanged. */
    public static SemanticMutant emptyStudentTests(String path, String source, String variant) {
        String rule = "student-test-assertions";
        return new SemanticMutant(rule, path, source, variant, new ContractWitness(rule, "", "", "learner-owned JUnit test bodies contain no checks"), null,
                Evidence.EMPTY_STUDENT_TESTS);
    }

    /** A learner test variant preserving actions while suppressing assertion failures. */
    public static SemanticMutant nonFailingStudentTests(String path, String source, String variant) {
        String rule = "student-test-assertion-failures";
        return new SemanticMutant(rule, path, source, variant, new ContractWitness(rule, "", "", "learner test actions run but assertion failures are suppressed"), null,
                Evidence.NON_FAILING_STUDENT_TESTS);
    }

    /** A pure test-writing candidate whose assigned test bodies fail regardless of the implementation under test. */
    public static SemanticMutant alwaysFailingStudentTests(String path, String source, String variant) {
        String rule = "student-tests-always-failing";
        return new SemanticMutant(rule, path, source, variant, new ContractWitness(rule, "", "", "every assigned learner test unconditionally throws an assertion error"), null,
                Evidence.ALWAYS_FAILING_STUDENT_TESTS);
    }

    /**
     * Identifies the independently rewritten learner method in diagnostics and retained assessment obligations.
     *
     * @param method name of the learner method whose body changed
     * @return the same probe with method-specific identity and diagnostic
     */
    public SemanticMutant forStudentTestMethod(String method) {
        if (!testsLearnerAssertions()) {
            throw new IllegalStateException("Only learner-test probes have a student method");
        }
        String rule = ruleId + "#" + method;
        var description = new ContractWitness(rule, "", "", counterexample.wrongBehavior() + " in " + method);
        return new SemanticMutant(rule, solutionPath, originalSolutionSource, mutantSource, description, reviewTarget, evidence);
    }

    /** Whether this probe tests the effect of learner-owned test assertions. */
    public boolean testsLearnerAssertions() {
        return evidence == Evidence.EMPTY_STUDENT_TESTS || evidence == Evidence.NON_FAILING_STUDENT_TESTS || evidence == Evidence.ALWAYS_FAILING_STUDENT_TESTS;
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
