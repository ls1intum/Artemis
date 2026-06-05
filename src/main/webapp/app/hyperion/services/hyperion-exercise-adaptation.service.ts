import { Injectable, inject } from '@angular/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { HyperionExerciseGenerationService } from 'app/hyperion/services/hyperion-exercise-generation.service';

/**
 * Triggers an agentic exercise adaptation run from the review system.
 *
 * Adaptation reuses the same endpoint as whole-exercise generation: the {@code prompt} carries the feedback to address (here, a review thread's
 * finding plus optional instructor instructions) instead of a create brief. This service is the single seam the review editors call so the comment
 * widget itself stays dumb and never talks to HTTP. Progress is not shown here: the run is keyed by exercise id on the server and the compact run
 * card on the exercise detail page reattaches to it via the {@code /status} endpoint, so the instructor sees live progress there. We surface an
 * info alert pointing them at it and never duplicate the run-card logic.
 */
@Injectable({ providedIn: 'root' })
export class HyperionExerciseAdaptationService {
    private generationService = inject(HyperionExerciseGenerationService);
    private alertService = inject(AlertService);

    /**
     * Starts an adaptation run for the exercise using the assembled feedback as the prompt.
     *
     * @param exerciseId The exercise to adapt.
     * @param feedback The human-readable feedback to address (thread finding plus optional instructions).
     */
    adaptExercise(exerciseId: number, feedback: string): void {
        const trimmedFeedback = feedback.trim();
        if (!trimmedFeedback) {
            return;
        }
        this.generationService.generateExercise(exerciseId, trimmedFeedback).subscribe({
            next: () => this.alertService.info('artemisApp.review.adaptExercise.started'),
            // A 409 means a run is already in progress for this exercise; everything else is a generic start failure.
            error: (error: { status?: number }) =>
                this.alertService.error(error?.status === 409 ? 'artemisApp.review.adaptExercise.alreadyRunning' : 'artemisApp.review.adaptExercise.startError'),
        });
    }
}
