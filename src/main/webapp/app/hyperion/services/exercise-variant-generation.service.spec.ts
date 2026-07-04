import { describe, it } from 'vitest';

/**
 * Vitest specs for ExerciseVariantGenerationService (plan Sections 5.2–5.4, 10).
 */
describe('ExerciseVariantGenerationService', () => {
    // TODO (Sonnet): Implement per plan Section 10:
    // - startGeneration posts the request (intent fields by presence — no changeX booleans, no title; plan
    // Section 5.1), adds a running entry, and subscribes to the per-job websocket topic
    // "/user/topic/hyperion/variant-generation/jobs/{jobId}" (mock HyperionWebsocketService);
    // - PHASE_CHANGED / ATTEMPT / STEP_OUTPUT events update the matching job entry's signals;
    // - DONE with warnings transitions the entry to DRAFT_WITH_WARNINGS and stores variantExerciseId;
    // - terminal events unsubscribe from the topic; loadJobs() re-syncs from REST on reconnect (authoritative,
    // plan Section 5.4 "State handling");
    // - cancelJob issues DELETE and the entry transitions to CANCELLED on the CANCELLED event;
    // - getActiveJob maps 204 to undefined (wizard resume, plan Section 5.3 point 5).
    it.todo('TODO (Sonnet): implement service specs (plan Section 10)');
});
