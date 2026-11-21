import { Component, DestroyRef, ErrorHandler, OnInit, computed, inject, signal } from '@angular/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import dayjs from 'dayjs/esm';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, merge, switchMap, tap } from 'rxjs';
import { ExerciseCacheService } from 'app/exercise/services/exercise-cache.service';
import { ResultTemplateStatus, evaluateTemplateStatus } from 'app/exercise/result/result.utils';
import { FeedbackComponent } from '../feedback.component';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';

@Component({
    selector: 'jhi-standalone-feedback',
    templateUrl: './standalone-feedback.component.html',
    imports: [FeedbackComponent],
})
export class StandaloneFeedbackComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private exerciseService = inject(ExerciseService);
    private exerciseCacheService = inject(ExerciseCacheService, { optional: true });

    private destroyRef = inject(DestroyRef);
    private errorHandler = inject(ErrorHandler);

    readonly exercise = signal<Exercise | undefined>(undefined);
    readonly result = signal<Result | undefined>(undefined);
    readonly resultsToDisplay = computed(() => {
        const result = this.result();
        return this.exercise() && result ? [result] : [];
    });
    readonly participation = signal<Participation>(undefined!);

    readonly showMissingAutomaticFeedbackInformation = signal(false);
    readonly messageKey = signal<string | undefined>(undefined);

    readonly latestDueDate = signal<dayjs.Dayjs | undefined>(undefined);

    ngOnInit(): void {
        this.route.params
            .pipe(
                switchMap((params) => {
                    const exerciseId = Number(params['exerciseId']);
                    const participationId = Number(params['participationId']);
                    const resultId = Number(params['resultId']);
                    this.result.set(undefined);
                    this.latestDueDate.set(undefined);
                    this.showMissingAutomaticFeedbackInformation.set(false);

                    const handleLoadError = (error: unknown) => {
                        this.errorHandler.handleError(error);
                        return EMPTY;
                    };
                    return merge(
                        this.exerciseService.getExerciseDetails(exerciseId).pipe(
                            tap((exerciseResponse) => {
                                const exercise = exerciseResponse.body!.exercise;
                                const participation = exercise?.studentParticipations?.find((candidate) => candidate.id === participationId);
                                if (participation) {
                                    participation.exercise = exercise;
                                    this.participation.set(participation);
                                }
                                const result = getAllResultsOfAllSubmissions(participation?.submissions).find((candidate) => candidate.id === resultId);
                                this.exercise.set(exercise);
                                this.result.set(result);
                                // The mobile application reports the build state independently.
                                const templateStatus = evaluateTemplateStatus(exercise, participation, result, false);
                                this.messageKey.set(templateStatus === ResultTemplateStatus.MISSING ? 'artemisApp.result.notLatestSubmission' : undefined);
                                this.setup();
                            }),
                            catchError(handleLoadError),
                        ),
                        (this.exerciseCacheService ?? this.exerciseService).getLatestDueDate(exerciseId).pipe(
                            tap((latestDueDate) => {
                                this.latestDueDate.set(latestDueDate);
                                this.setup();
                            }),
                            catchError(handleLoadError),
                        ),
                    );
                }),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe();
    }

    private setup() {
        const exercise = this.exercise();
        if (exercise && this.result()) {
            const latestDueDate = this.latestDueDate();
            if (latestDueDate) {
                this.showMissingAutomaticFeedbackInformation.set(dayjs().isBefore(latestDueDate));
            }
        }
    }
}
