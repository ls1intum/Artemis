import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, Subscription, map, tap } from 'rxjs';
import { HyperionExerciseVariantApi } from 'app/openapi/api/hyperion-exercise-variant-api';
import { VariantGenerationRequest } from 'app/openapi/model/variant-generation-request';
import { VariantJob } from 'app/openapi/model/variant-job';
import { VariantJobDetail } from 'app/openapi/model/variant-job-detail';
import { ExerciseVariantWebsocketService, VariantGenerationEvent, isTerminalVariantPhase } from 'app/hyperion/services/exercise-variant-websocket.service';
import { cloneWith } from 'app/foundation/util/deep-clone.util';

/**
 * Client service for AI exercise-variant generation. Two responsibilities:
 * 1. REST access to the variant endpoints via the generated OpenAPI client.
 * 2. Signal-based job-tray state shared by the navbar tray and the wizard: the `jobs` signal is the
 *    client-side copy of the user's job list, kept live by the per-job websocket topics and re-synced from
 *    REST on demand (events are fire-and-forget; the server-side job record is authoritative).
 */
@Injectable({ providedIn: 'root' })
export class ExerciseVariantGenerationService {
    private readonly api = inject(HyperionExerciseVariantApi);
    private readonly websocketService = inject(ExerciseVariantWebsocketService);

    private readonly jobSubscriptions = new Map<string, Subscription>();

    /** All jobs of the current user (running + retained-finished), authoritative copy of GET /variant-jobs. */
    readonly jobs = signal<VariantJob[]>([]);

    /** Running jobs drive the tray spinner ring + count badge. */
    readonly runningJobs = computed(() => this.jobs().filter((job) => !isTerminalVariantPhase(job.phase)));

    /** Tray button hidden when the user has no variant jobs at all. */
    readonly hasJobs = computed(() => this.jobs().length > 0);

    /**
     * Starts a variant-generation job and attaches to its websocket topic.
     *
     * @param exerciseId the source exercise id
     * @param request    the wizard request (intents by field presence, placement)
     * @param sourceExerciseTitle shown in the tray until the next REST re-sync delivers the server-side copy
     * @returns the created job id
     */
    startGeneration(exerciseId: number, request: VariantGenerationRequest, sourceExerciseTitle?: string): Observable<string> {
        return this.api.generateVariant(exerciseId, request).pipe(
            map((response) => response.jobId!),
            tap((jobId) => {
                this.upsertJob({ jobId, sourceExerciseId: exerciseId, sourceExerciseTitle, request, phase: 'ANALYZING' });
                this.attachToJob(jobId);
            }),
        );
    }

    /**
     * Re-syncs the tray list from REST — called on login, tray open, and websocket reconnect: events are
     * fire-and-forget, so the server-side job record is authoritative.
     */
    loadJobs(): Observable<VariantJob[]> {
        return this.api.getJobsOfCurrentUser().pipe(
            tap((jobs) => {
                this.jobs.set(jobs);
                const stillRunning = new Set(jobs.filter((job) => !isTerminalVariantPhase(job.phase) && job.jobId).map((job) => job.jobId!));
                // REST is authoritative: a job that finished while its terminal event was missed must lose its
                // subscription here, otherwise the topic stays attached for the rest of the session.
                this.jobSubscriptions.forEach((subscription, jobId) => {
                    if (!stillRunning.has(jobId)) {
                        subscription.unsubscribe();
                        this.websocketService.unsubscribeFromJob(jobId);
                        this.jobSubscriptions.delete(jobId);
                    }
                });
                stillRunning.forEach((jobId) => this.attachToJob(jobId));
            }),
        );
    }

    /** Clears the tray state and detaches all websocket subscriptions — called on logout. */
    clearJobs(): void {
        this.jobSubscriptions.forEach((subscription, jobId) => {
            subscription.unsubscribe();
            this.websocketService.unsubscribeFromJob(jobId);
        });
        this.jobSubscriptions.clear();
        this.jobs.set([]);
    }

    /** Full job detail incl. per-phase step outputs — reopening the modal in monitor mode. */
    getJobDetail(jobId: string): Observable<VariantJobDetail> {
        return this.api.getJobDetail(jobId);
    }

    /**
     * Requests cooperative cancellation. The entry stays listed; it transitions to CANCELLED when the
     * CANCELLED websocket event arrives (the clone cleanup happens server-side first).
     */
    cancelJob(jobId: string): Observable<void> {
        return this.api.cancelJob(jobId);
    }

    /**
     * Per-job event stream for the wizard's step timeline. Also drives this service's tray state internally —
     * subscribing here does not create a second websocket subscription.
     */
    jobEvents(jobId: string): Observable<VariantGenerationEvent> {
        this.attachToJob(jobId);
        return this.websocketService.subscribeToJob(jobId);
    }

    private attachToJob(jobId: string): void {
        if (this.jobSubscriptions.has(jobId)) {
            return;
        }
        const subscription = this.websocketService.subscribeToJob(jobId).subscribe((event) => this.handleEvent(jobId, event));
        this.jobSubscriptions.set(jobId, subscription);
    }

    private handleEvent(jobId: string, event: VariantGenerationEvent): void {
        this.jobs.update((jobs) =>
            jobs.map((job) => {
                if (job.jobId !== jobId) {
                    return job;
                }
                return cloneWith(job, {
                    phase: event.phase ?? job.phase,
                    attempt: event.attempt ?? job.attempt,
                    maxAttempts: event.maxAttempts ?? job.maxAttempts,
                    variantExerciseId: event.variantExerciseId ?? job.variantExerciseId,
                    warnings: event.warnings ?? job.warnings,
                });
            }),
        );
        if (event.type === 'DONE' || event.type === 'FAILED' || event.type === 'CANCELLED') {
            // The terminal event does not carry the final record: CANCELLED has neither a variantExerciseId nor a
            // failureDetail, so the merge above keeps whatever provisioning had cached — a cancellation whose clone
            // cleanup SUCCEEDED would keep offering a link to an exercise that no longer exists. Take the server's
            // copy instead; it is the authority on what survived.
            this.api.getJobDetail(jobId).subscribe({
                next: (detail) => {
                    if (detail.job) {
                        this.replaceJob(jobId, detail.job);
                    }
                },
                // A failed refresh leaves the merged entry in place; the next tray re-sync corrects it.
                error: () => {},
            });
            // This handler is the FIRST subscriber on the shared per-job subject, and detaching completes that
            // subject — done synchronously it would stop later subscribers (the wizard modal) before the
            // subject's next() loop delivers this terminal event to them, freezing the modal in the last live
            // phase. Detaching in a microtask lets every subscriber receive the event first.
            window.queueMicrotask(() => this.detachFromJob(jobId));
        }
    }

    private detachFromJob(jobId: string): void {
        this.jobSubscriptions.get(jobId)?.unsubscribe();
        this.jobSubscriptions.delete(jobId);
        this.websocketService.unsubscribeFromJob(jobId);
    }

    private upsertJob(job: VariantJob): void {
        this.jobs.update((jobs) => {
            const existingIndex = jobs.findIndex((existing) => existing.jobId === job.jobId);
            if (existingIndex >= 0) {
                const updated = jobs.slice();
                updated[existingIndex] = cloneWith(updated[existingIndex], job);
                return updated;
            }
            return [job].concat(jobs);
        });
    }

    /**
     * Replaces a listed job with the server's copy, dropping the fields the event merge had carried over. Unlike
     * {@link upsertJob} this overwrites rather than merges — that is the point, since a value the server no longer
     * reports (a cleaned-up clone's exercise id) has to disappear — and it never re-adds a job that has since left
     * the list.
     */
    private replaceJob(jobId: string, job: VariantJob): void {
        this.jobs.update((jobs) => jobs.map((existing) => (existing.jobId === jobId ? job : existing)));
    }
}
