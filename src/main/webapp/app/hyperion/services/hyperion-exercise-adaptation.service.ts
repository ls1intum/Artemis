import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { AlertService } from 'app/foundation/service/alert.service';
import { ExerciseGenerationJobStart, HyperionExerciseGenerationService } from 'app/hyperion/services/hyperion-exercise-generation.service';

/**
 * Starts an adapt run via the whole-exercise generation endpoint, with the {@code prompt} carrying the feedback instead of a create
 * brief. Progress is not shown here: the embedded run card reattaches to the exercise's run via {@code /status}.
 */
@Injectable({ providedIn: 'root' })
export class HyperionExerciseAdaptationService {
    private generationService = inject(HyperionExerciseGenerationService);
    private alertService = inject(AlertService);

    /**
     * Starts an adaptation run using the assembled feedback as the prompt. The returned {@link Observable} is cold; the host is the
     * single subscriber and uses its {@code next} emission as the cue to call the run card's {@code reattach()}. Info/error alerts are
     * wired in here via {@code tap}. Returns {@code undefined} when the feedback is empty (nothing to start).
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
