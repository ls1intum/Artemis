import { Component, OnInit, inject, signal } from '@angular/core';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import dayjs from 'dayjs/esm';
import { ExerciseDetailsType, ExerciseService } from 'app/exercise/services/exercise.service';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { ExerciseCacheService } from 'app/exercise/services/exercise-cache.service';
import { ResultTemplateStatus, evaluateTemplateStatus } from 'app/exercise/result/result.utils';
import { FeedbackComponent } from '../feedback.component';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';

@Component({
    selector: 'jhi-standalone-feedback',
    templateUrl: './standalone-feedback.component.html',
    styleUrls: ['standalone-feedback.scss'],
    imports: [FeedbackComponent],
})
export class StandaloneFeedbackComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private exerciseService = inject(ExerciseService);
    private exerciseCacheService = inject(ExerciseCacheService, { optional: true });

    readonly exercise = signal<Exercise | undefined>(undefined);
    readonly result = signal<Result | undefined>(undefined);
    readonly participation = signal<Participation>(undefined!);

    readonly showMissingAutomaticFeedbackInformation = signal(false);
    readonly messageKey = signal<string | undefined>(undefined);
    readonly exerciseType = signal<ExerciseType>(undefined!);

    readonly latestDueDate = signal<dayjs.Dayjs | undefined>(undefined);

    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            const exerciseId = parseInt(params['exerciseId'], 10);
            const participationId = parseInt(params['participationId'], 10);
            const resultId = parseInt(params['resultId'], 10);

            this.exerciseService.getExerciseDetails(exerciseId).subscribe((exerciseResponse: HttpResponse<ExerciseDetailsType>) => {
                const exercise = exerciseResponse.body!.exercise;
                this.exercise.set(exercise);
                const participation = exercise?.studentParticipations?.find((participation) => participation.id === participationId);
                if (participation) {
                    participation.exercise = exercise;
                    this.participation.set(participation);
                }

                const relevantResult = getAllResultsOfAllSubmissions(participation?.submissions).find((result) => result.id == resultId);

                this.result.set(relevantResult);

                // We set isBuilding here to false. It is the mobile applications responsibility to make the user aware if a participation is being built
                const templateStatus = evaluateTemplateStatus(exercise, participation, relevantResult, false);
                if (templateStatus == ResultTemplateStatus.MISSING) {
                    this.messageKey.set('artemisApp.result.notLatestSubmission');
                } else {
                    this.messageKey.set(undefined);
                }

                this.setup();
            });

            (this.exerciseCacheService ?? this.exerciseService).getLatestDueDate(exerciseId).subscribe((latestDueDate) => {
                this.latestDueDate.set(latestDueDate);
                this.setup();
            });
        });
    }

    private setup() {
        const exercise = this.exercise();
        if (exercise && this.result()) {
            this.exerciseType.set(exercise.type!);

            const latestDueDate = this.latestDueDate();
            if (latestDueDate) {
                this.showMissingAutomaticFeedbackInformation.set(dayjs().isBefore(latestDueDate));
            }
        }
    }
}
