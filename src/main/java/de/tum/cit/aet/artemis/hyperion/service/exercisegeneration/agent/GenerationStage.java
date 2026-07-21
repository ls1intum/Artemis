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
     * Write {@code /workspace/SPEC.md}: the behavioural specification — archetype, numbered rules with real computation, and a worked-examples table whose arithmetic is
     * machine-checked in the sandbox. Run only when no non-trivial instructor statement exists; an instructor draft IS the spec.
     */
    SPEC("spec", "Specification"),

    /** Write {@code /workspace/DESIGN.md}: the class table, public API, task/test-partition table, and diagram decision the later stages build from. */
    DESIGN("design", "Design"),

    /** Implement the reference solution per {@code DESIGN.md} and replay every worked example from the requirements against it in the sandbox. */
    SOLUTION("solution", "Solution"),

    /** Derive the student-facing template from the finished solution by removing exactly the student work {@code DESIGN.md} marks stubbed or absent. */
    TEMPLATE("template", "Template"),

    /** Author the differential tests one task partition at a time from {@code DESIGN.md}'s task table, verifying each against both solution and template. */
    TESTS("tests", "Tests"),

    /** Write the student-facing problem statement last, from {@code DESIGN.md} and the verified test names. */
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
