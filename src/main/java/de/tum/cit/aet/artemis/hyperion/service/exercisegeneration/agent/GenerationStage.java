package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent;

/**
 * A single stage of the orchestrator-enforced staged generation workflow, run as its own bounded agent loop with its own stage-scoped system prompt
 * ({@link AgentSystemPromptService#buildStage(de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise, GenerationStage)}). The orchestrator runs the stages in this
 * declared order, gating each stage's output before starting the next one; within a stage, the agent's {@code submit} tool call means only that this stage's artifact is
 * finished, not that the exercise is complete.
 * <p>
 * This mirrors the dependency order already described as STAGE 0-4 in the legacy single-loop
 * {@link AgentSystemPromptService#build(de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise)}
 * workflow text, which remains the fallback path for callers that do not stage the run.
 */
public enum GenerationStage {

    /**
     * Write {@code /workspace/SPEC.md}: the unified specification — archetype, numbered rules with real computation, a worked-examples table whose arithmetic is
     * machine-checked in the sandbox, the design (classes with template status and state ownership), and the testing strategy (weights, hidden variants, diagram decision).
     * One planning artifact instead of a spec/design pair, so the two can never drift apart. Skipped when the instructor provided a real statement — that statement is the spec.
     */
    SPEC("spec", "Specification"),

    /** Implement the reference solution per the specification and replay every worked example against it in the sandbox. */
    SOLUTION("solution", "Solution"),

    /** Derive the student-facing template from the finished solution by removing exactly the student work the specification marks stubbed or student-created. */
    TEMPLATE("template", "Template"),

    /** Author the differential tests per the specification's testing strategy, verifying each against both solution and template. */
    TESTS("tests", "Tests"),

    /** Write the student-facing problem statement last, by rewriting the specification with the verified test names. */
    STATEMENT("statement", "Statement");

    private final String id;

    private final String displayName;

    GenerationStage(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /**
     * @return the stable, lowercase identifier for this stage (e.g. for logs and progress messages)
     */
    public String id() {
        return id;
    }

    /**
     * @return the human-readable name of this stage, suitable for a progress message (e.g. "Template")
     */
    public String displayName() {
        return displayName;
    }
}
