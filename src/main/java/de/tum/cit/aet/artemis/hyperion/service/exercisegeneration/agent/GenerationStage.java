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
    SPEC("Specification", "spec"),

    /** Build the solution, derived template, behavioral tests, and grading plan together in risk-chosen vertical increments. */
    TESTS("Executable build", "artifacts"),

    /** Write the student-facing problem statement last, by rewriting the specification with the verified test names. */
    STATEMENT("Statement", "statement");

    private final String displayName;

    private final String activityStep;

    GenerationStage(String displayName, String activityStep) {
        this.displayName = displayName;
        this.activityStep = activityStep;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * The stable machine-readable substep key streamed to the client, so it need not parse the prose stage label. Kept separate from {@link #name()} because the enum constant is
     * an internal name the client must not depend on ({@code TESTS} authors solution, template, tests, and grading plan together, which is what {@code artifacts} says).
     *
     * @return the substep key of this stage
     */
    public String activityStep() {
        return activityStep;
    }
}
