import { Component, OnInit, Optional, ViewChild } from '@angular/core';
import { Exercise } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import dayjs from 'dayjs/esm';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ActivatedRoute } from '@angular/router';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { HttpResponse } from '@angular/common/http';
import { FeedbackComponent } from 'app/exercises/shared/feedback/feedback.component';
import { ExerciseCacheService } from 'app/exercises/shared/exercise/exercise-cache.service';

@Component({
    selector: 'jhi-standalone-feedback',
    templateUrl: './standalone-feedback.component.html',
    styleUrls: ['./standalone-feedback.component.scss'],
})
export class StandaloneFeedbackComponent implements OnInit {
    public exercise?: Exercise;
    public result?: Result;

    private latestDueDate?: dayjs.Dayjs;

    @ViewChild('feedbackComponent')
    private feedbackComponent: FeedbackComponent;

    constructor(
        public route: ActivatedRoute,
        private exerciseService: ExerciseService,
        private resultService: ResultService,
        @Optional() private exerciseCacheService: ExerciseCacheService,
    ) {}

    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            const exerciseId = parseInt(params['exerciseId'], 10);
            const resultId = parseInt(params['resultId'], 10);

            const isTemplateStatusMissing = params['isTemplateStatusMissing'] == 'true';

            this.exerciseService.getExerciseDetails(exerciseId).subscribe((exerciseResponse: HttpResponse<Exercise>) => {
                this.exercise = exerciseResponse.body!;
                this.setup(isTemplateStatusMissing);
            });

            this.resultService.find(resultId).subscribe((resultResponse) => {
                this.result = resultResponse.body!;
                this.setup(isTemplateStatusMissing);
            });

            (this.exerciseCacheService ?? this.exerciseService).getLatestDueDate(exerciseId).subscribe((latestDueDate) => {
                if (latestDueDate) {
                    this.feedbackComponent.showMissingAutomaticFeedbackInformation = dayjs().isBefore(latestDueDate);
                    this.feedbackComponent.latestDueDate = this.latestDueDate;
                }
            });
        });
    }

    private setup(isTemplateStatusMissing: boolean) {
        if (this.exercise != null && this.result != null) {
            this.feedbackComponent.exerciseType = this.exercise.type!;
            this.feedbackComponent.showScoreChart = true;
        }

        if (isTemplateStatusMissing) {
            this.feedbackComponent.messageKey = 'artemisApp.result.notLatestSubmission';
        }
    }
}
