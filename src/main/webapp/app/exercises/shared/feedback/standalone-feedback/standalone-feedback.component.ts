import { Component, OnInit, Optional } from '@angular/core';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import dayjs from 'dayjs/esm';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { ExerciseCacheService } from 'app/exercises/shared/exercise/exercise-cache.service';

@Component({
    selector: 'jhi-standalone-feedback',
    templateUrl: './standalone-feedback.component.html',
    styleUrls: ['./standalone-feedback.component.scss'],
})
export class StandaloneFeedbackComponent implements OnInit {
    public exercise?: Exercise;
    public result?: Result;

    public isTemplateStatusMissing = false;
    public showMissingAutomaticFeedbackInformation = false;
    public messageKey?: string = undefined;
    public exerciseType?: ExerciseType = undefined;

    public latestDueDate?: dayjs.Dayjs;

    constructor(public route: ActivatedRoute, private exerciseService: ExerciseService, @Optional() private exerciseCacheService: ExerciseCacheService) {}

    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            const exerciseId = parseInt(params['exerciseId'], 10);
            const participationId = parseInt(params['participationId'], 10);
            const resultId = parseInt(params['resultId'], 10);

            this.isTemplateStatusMissing = params['isTemplateStatusMissing'] == 'true';

            this.exerciseService.getExerciseDetails(exerciseId).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
                this.exercise = exerciseResponse.body!;
                const participation = this.exercise?.studentParticipations?.find((participation) => participation.id == participationId);
                if (participation != null) {
                    participation.exercise = this.exercise;
                }

                const relevantResult = participation?.results?.find((result) => result.id == resultId);
                if (relevantResult != null) {
                    relevantResult.participation = participation;
                }

                this.result = relevantResult;

                this.setup();
            });

            (this.exerciseCacheService ?? this.exerciseService).getLatestDueDate(exerciseId).subscribe((latestDueDate) => {
                this.latestDueDate = latestDueDate;
                this.setup();
            });
        });
    }

    private setup() {
        if (this.exercise != null && this.result != null) {
            this.exerciseType = this.exercise.type!;

            if (this.latestDueDate) {
                this.showMissingAutomaticFeedbackInformation = dayjs().isBefore(this.latestDueDate);
            }

            if (this.isTemplateStatusMissing) {
                this.messageKey = 'artemisApp.result.notLatestSubmission';
            }
        }
    }
}
