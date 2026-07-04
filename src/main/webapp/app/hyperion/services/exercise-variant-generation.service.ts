import { Injectable, computed, signal } from '@angular/core';

/**
 * Client service for AI exercise-variant generation (plan Section 5.3, point 1).
 * Two responsibilities:
 * 1. REST access to the variant endpoints (plan Section 5.1) — via the regenerated OpenAPI client
 *    (like `hyperionCodeGenerationApi`), NOT hand-rolled HttpClient calls.
 * 2. Signal-based job-tray state shared by the navbar tray and the wizard (plan Section 5.4).
 *
 * TODO (Sonnet): After implementing the server resource, regenerate the OpenAPI client so
 * `app/openapi/api/hyperionExerciseVariantApi.service.ts` and the models (VariantJobDTO, VariantJobDetailDTO,
 * VariantGenerationRequestDTO, VariantGenerationEventDTO, VariantJobPhase — the shared phase enum, single source
 * of truth per plan Section 5.2) exist; then replace the placeholder types below with the generated ones.
 */

// TODO (Sonnet): DELETE these placeholders once the OpenAPI client is regenerated (see above).
export type VariantJobPhase =
    | 'ANALYZING'
    | 'PLANNING'
    | 'PROVISIONING'
    | 'TRANSFORMING'
    | 'VERIFYING'
    | 'REPAIRING'
    | 'FINALIZING'
    | 'COMPLETED'
    | 'DRAFT_WITH_WARNINGS'
    | 'FAILED'
    | 'CANCELLED';

export interface VariantJob {
    jobId: string;
    sourceExerciseId: number;
    sourceExerciseTitle: string;
    exerciseType: string;
    phase: VariantJobPhase;
    attempt?: number;
    maxAttempts?: number;
    variantExerciseId?: number;
    warnings?: string[];
}

@Injectable({ providedIn: 'root' })
export class ExerciseVariantGenerationService {
    // TODO (Sonnet): inject() the generated hyperionExerciseVariantApi service, HyperionWebsocketService
    // (reuse its subscribeToJob pattern, plan Section 5.3 point 2), and AccountService (subscribe on login,
    // plan Section 5.4 "State handling").

    /** All jobs of the current user (running + retained-finished), authoritative copy of GET /variant-jobs. */
    readonly jobs = signal<VariantJob[]>([]);

    /** Running jobs drive the tray spinner ring + count badge (plan Section 5.4). */
    readonly runningJobs = computed(() => this.jobs().filter((job) => !isTerminalPhase(job.phase)));

    /** Tray button hidden when the user has no jobs at all (plan Section 5.4). */
    readonly hasJobs = computed(() => this.jobs().length > 0);

    /**
     * TODO (Sonnet): startGeneration(exerciseId, request): POST /exercises/{id}/generate-variant via the OpenAPI
     * service; on success add a synthetic running entry to `jobs` and subscribe to
     * "/user/topic/hyperion/variant-generation/jobs/{jobId}" (plan Section 5.2 topic). Return the jobId (Observable).
     */

    /**
     * TODO (Sonnet): getActiveJob(exerciseId): GET .../generate-variant/active — wizard resume support on open
     * (plan Section 5.3, point 5); 204 → undefined.
     */

    /**
     * TODO (Sonnet): loadJobs(): GET /variant-jobs → jobs.set(...); called on login and on websocket reconnect —
     * events are fire-and-forget, the REST list is authoritative (plan Section 5.4, "State handling").
     */

    /**
     * TODO (Sonnet): getJobDetail(jobId): GET /variant-jobs/{jobId} — full step outputs for reopening the modal in
     * monitor mode (plan Section 5.4, "Clicking any job entry reopens the generation modal").
     */

    /**
     * TODO (Sonnet): cancelJob(jobId): DELETE /variant-jobs/{jobId}; optimistically keep the entry, transition it to
     * CANCELLED when the CANCELLED websocket event arrives (plan Sections 5.2 and 5.4 — cancel is offered in the
     * tray AND in the running modal, both behind a confirmation dialog: "cancellation discards the LLM work done so
     * far and deletes the provisioned clone").
     */

    /**
     * TODO (Sonnet): private handleEvent(jobId, event: VariantGenerationEventDTO): update the matching entry in
     * `jobs` (phase, attempt, detail, variantExerciseId, warnings); unsubscribe from the job topic on terminal
     * events (plan Section 5.4). Expose a per-job observable/signal the wizard subscribes to for its step timeline
     * (PHASE_CHANGED / PROGRESS / ATTEMPT / STEP_OUTPUT / DONE / FAILED / CANCELLED, plan Section 5.2).
     */
}

/** TODO (Sonnet): replace with the OpenAPI-generated enum helper once available (plan Section 5.2). */
export function isTerminalPhase(phase: VariantJobPhase): boolean {
    return phase === 'COMPLETED' || phase === 'DRAFT_WITH_WARNINGS' || phase === 'FAILED' || phase === 'CANCELLED';
}
