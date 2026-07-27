package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

/**
 * A single stage of the orchestrator-enforced staged generation workflow, run as its own bounded agent loop with its own stage-scoped system prompt
 * ({@link AgentSystemPromptService#buildStage(de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise, GenerationStage)}). The orchestrator runs the stages in this
 * declared order, gating each stage's output before starting the next one; within a stage, the agent's {@code submit} tool call means only that this stage's artifact is
 * finished, not that the exercise is complete.
 * <p>
 * The generation runner only ever runs {@link #SPEC}, {@link #TESTS} (the coherent executable build), and {@link #STATEMENT}. {@link #SOLUTION} and {@link #TEMPLATE} are no
 * longer reachable from any production entry point — the staged runner's stage order omits them and the single-loop fallback is selected by generation mode, not by stage — but
 * their prompts and gates are still wired end to end.
 */
public enum GenerationStage {

    /**
     * Write {@code /workspace/SPEC.md}: the unified specification — numbered rules with real computation, a worked-examples table whose arithmetic is machine-checked in the
     * sandbox, the design (classes with template status and state ownership), and the testing strategy (weights, hidden variants, diagram decision).
     * One planning artifact instead of a spec/design pair, so the two can never drift apart. Skipped when the instructor provided a real statement — that statement is the spec.
     */
    SPEC("Specification"),

    /** Implement the reference solution per the specification and replay every worked example against it in the sandbox. */
    SOLUTION("Solution"),

    /** Derive the student-facing template from the finished solution by removing exactly the student work the specification marks stubbed or student-created. */
    TEMPLATE("Template"),

    /** Build the solution, derived template, behavioral tests, and grading plan together in risk-chosen vertical increments. */
    TESTS("Executable build"),

    /** Write the student-facing problem statement last, by rewriting the specification with the verified test names. */
    STATEMENT("Statement");

    private final String displayName;

    GenerationStage(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return the human-readable name of this stage, suitable for a progress message (e.g. "Template")
     */
    public String displayName() {
        return displayName;
    }
}
