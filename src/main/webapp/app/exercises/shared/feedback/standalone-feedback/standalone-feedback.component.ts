import { Component, OnInit, inject } from '@angular/core';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import dayjs from 'dayjs/esm';
import { ExerciseDetailsType, ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { ExerciseCacheService } from 'app/exercises/shared/exercise/exercise-cache.service';
import { ResultTemplateStatus, evaluateTemplateStatus } from 'app/exercises/shared/result/result.utils';

@Component({
    selector: 'jhi-standalone-feedback',
    templateUrl: './standalone-feedback.component.html',
    styleUrls: ['./../feedback.scss', 'standalone-feedback.scss'],
})
export class StandaloneFeedbackComponent implements OnInit {
    route = inject(ActivatedRoute);
    private exerciseService = inject(ExerciseService);
    private exerciseCacheService = inject(ExerciseCacheService, { optional: true })!;

    exercise?: Exercise;
    result?: Result;

    showMissingAutomaticFeedbackInformation = false;
    messageKey?: string;
    exerciseType?: ExerciseType;

    latestDueDate?: dayjs.Dayjs;

    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            const exerciseId = parseInt(params['exerciseId'], 10);
            const participationId = parseInt(params['participationId'], 10);
            const resultId = parseInt(params['resultId'], 10);

            this.exerciseService.getExerciseDetails(exerciseId).subscribe((exerciseResponse: HttpResponse<ExerciseDetailsType>) => {
                this.exercise = exerciseResponse.body!.exercise;
                const participation = this.exercise?.studentParticipations?.find((participation) => participation.id === participationId);
                if (participation) {
                    participation.exercise = this.exercise;
                }

                const relevantResult = participation?.results?.find((result) => result.id == resultId);
                if (relevantResult) {
                    relevantResult.participation = participation;
                }

                this.result = relevantResult;

                // We set isBuilding here to false. It is the mobile applications responsibility to make the user aware if a participation is being built
                const templateStatus = evaluateTemplateStatus(this.exercise, participation, relevantResult, false);
                if (templateStatus == ResultTemplateStatus.MISSING) {
                    this.messageKey = 'artemisApp.result.notLatestSubmission';
                } else {
                    this.messageKey = undefined;
                }

                this.setup();
            });

            (this.exerciseCacheService ?? this.exerciseService).getLatestDueDate(exerciseId).subscribe((latestDueDate) => {
                this.latestDueDate = latestDueDate;
                this.setup();
            });
        });
    }

    private setup() {
        if (this.exercise && this.result) {
            this.exerciseType = this.exercise.type!;

            if (this.latestDueDate) {
                this.showMissingAutomaticFeedbackInformation = dayjs().isBefore(this.latestDueDate);
            }
        }
    }
}
