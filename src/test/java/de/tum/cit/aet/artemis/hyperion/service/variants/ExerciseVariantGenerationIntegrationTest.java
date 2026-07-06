package de.tum.cit.aet.artemis.hyperion.service.variants;

/**
 * Integration tests for the variant-generation pipeline (plan Section 10, "Server integration tests").
 * Established Hyperion test pattern: JUnit + Testcontainers/PostgreSQL + MOCKED ChatClient — see the
 * HyperionCodeGeneration*Test classes for base-class reuse and ChatClient mocking.
 */
class ExerciseVariantGenerationIntegrationTest {

    // TODO (Sonnet): Extend the appropriate Hyperion/module integration base class (mirror
    // HyperionCodeGenerationResourceTest) and implement, per plan Section 10:
    //
    // 1. Pipeline phase transitions incl. failure paths:
    // - happy path ANALYZING → ... → COMPLETED with a mocked ChatClient returning a canned ChangePlan and
    // canned tool calls (Section 2.7.2 state diagram is the spec).
    // - malformed planner output → 2 re-prompts → FAILED (Section 6 row 2).
    // - budget exhausted with red gates → DRAFT_WITH_WARNINGS, variant kept + warnings attached (Section 2.6).
    //
    // 2. Cooperative cancellation:
    // - flag set during TRANSFORMING is honored at the next boundary, provisioned clone is deleted, job →
    // CANCELLED, CANCELLED event published (Section 5.2).
    // - DELETE on a job in FINALIZING/terminal → 409 (Section 5.2).
    //
    // 3. Provisioning collision retry: pre-create an exercise with the colliding short name/project key; assert
    // the -V2 suffix retry succeeds and checkIfProjectExists was re-run (Section 6 row 1).
    //
    // 4. Scripted agent-loop runs: mock ChatClient returns canned tool calls (applyEdit/runBuild sequences);
    // assert repo edits land in the variant's repos and builds are triggered (Section 10).
    //
    // 5. Quiz adapter round-trip: generate a quiz variant with canned updateQuestion calls; assert
    // QuizExercise.isValid() on the result (Section 10).
    //
    // 6. REST layer: per-user scoping of variant-jobs endpoints (foreign job → 404), 400 on no-intent request,
    // 400 on unsupported exercise type; several jobs for the SAME exercise can run simultaneously (parallel
    // variant generation is an explicit requirement — there is deliberately no per-exercise dedup).
    //
    // 7. Exam placement: source in an exam exercise group → variant lands in the SAME exam exercise group;
    // non-exam exercise with SAME_EXAM_GROUP placement → 400 (Section 5.5).
}
