package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

/**
 * A single stage of the staged generation workflow, run as its own bounded agent loop with its own stage-scoped system prompt
 * ({@link AgentSystemPromptService#buildStage(de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise, GenerationStage)}). The orchestrator runs the stages in this
 * declared order, gating each stage's output before starting the next one; within a stage, {@code submit} means only that this stage's artifact is finished.
 */
public enum GenerationStage {

    /**
     * Write {@code /workspace/SPEC.md}: numbered rules with real computation, a worked-examples table whose arithmetic is machine-checked in the sandbox, the design (classes
     * with template status and state ownership), and the testing strategy (weights, hidden variants, diagram decision). Skipped when the instructor provided a real statement —
     * that statement is the spec.
     */
    SPEC("Specification"),

    /** Build the solution, derived template, behavioral tests, and grading plan together in risk-chosen vertical increments. */
    TESTS("Executable build"),

    /** Write the student-facing problem statement last, by rewriting the specification with the verified test names. */
    STATEMENT("Statement");

    private final String displayName;

    GenerationStage(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
