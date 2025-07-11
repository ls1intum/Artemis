import { Component, OnInit, inject } from '@angular/core';
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
    styleUrls: ['../feedback.scss', 'standalone-feedback.scss'],
    imports: [FeedbackComponent],
})
export class StandaloneFeedbackComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private exerciseService = inject(ExerciseService);
    private exerciseCacheService = inject(ExerciseCacheService, { optional: true });

    exercise?: Exercise;
    result?: Result;
    participation: Participation;

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
                    this.participation = participation;
                }

                const relevantResult = getAllResultsOfAllSubmissions(participation?.submissions).find((result) => result.id == resultId);

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
