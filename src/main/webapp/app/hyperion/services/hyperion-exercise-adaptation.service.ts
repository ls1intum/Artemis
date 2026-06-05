import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { AlertService } from 'app/foundation/service/alert.service';
import { ExerciseGenerationJobStart, HyperionExerciseGenerationService } from 'app/hyperion/services/hyperion-exercise-generation.service';

/**
 * Triggers an agentic exercise adaptation run from the review system.
 *
 * Adaptation reuses the same endpoint as whole-exercise generation: the {@code prompt} carries the feedback to address (a review thread's finding
 * plus optional instructor instructions, or free-text adapt instructions) instead of a create brief. This service is the single seam the review
 * editors and the instructor editor call so the callers stay dumb and never talk to HTTP. Progress is not shown here: the run is keyed by exercise id
 * on the server and the embedded run card reattaches to it via the {@code /status} endpoint, so the instructor sees live progress in-context. We
 * surface a brief info alert that the run started and never duplicate the run-card logic.
 */
@Injectable({ providedIn: 'root' })
export class HyperionExerciseAdaptationService {
    private generationService = inject(HyperionExerciseGenerationService);
    private alertService = inject(AlertService);

    /**
     * Starts an adaptation run for the exercise using the assembled feedback as the prompt.
     *
     * The returned {@link Observable} is COLD and unsubscribed: the host is the single subscriber, so exactly one POST is sent. The
     * brief info/error alerts are wired in here via {@code tap} so every caller gets consistent feedback without duplicating the toast
     * logic, while the {@code next} emission (the job is registered server-side) is the host's cue to call the run card's
     * {@code reattach()} and pick up live progress. Returns {@code undefined} when the feedback is empty (nothing to start).
     *
     * @param exerciseId The exercise to adapt.
     * @param feedback The human-readable feedback to address (thread finding plus optional instructions).
     * @returns The HTTP start stream (subscribe exactly once), or {@code undefined} when the feedback is empty.
     */
    adaptExercise(exerciseId: number, feedback: string): Observable<ExerciseGenerationJobStart> | undefined {
        const trimmedFeedback = feedback.trim();
        if (!trimmedFeedback) {
            return undefined;
        }
        return this.generationService.generateExercise(exerciseId, trimmedFeedback).pipe(
            tap({
                next: () => this.alertService.info('artemisApp.review.adaptExercise.started'),
                // A 409 means a run is already in progress for this exercise; everything else is a generic start failure.
                error: (error: { status?: number }) =>
                    this.alertService.error(error?.status === 409 ? 'artemisApp.review.adaptExercise.alreadyRunning' : 'artemisApp.review.adaptExercise.startError'),
            }),
        );
    }
}
