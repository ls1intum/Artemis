import { Component, OnInit } from '@angular/core';
import { ExerciseService, ExerciseType } from 'app/entities/exercise';
import { TextSubmission, TextSubmissionService } from 'app/entities/text-submission';
import { ActivatedRoute } from '@angular/router';
import { TextExercise } from 'app/entities/text-exercise';
import { DifferencePipe } from 'ngx-moment';
import { TranslateService } from '@ngx-translate/core';
import { HttpResponse } from '@angular/common/http';
import { Result } from 'app/entities/result';
import { TextAssessmentsService } from 'app/entities/text-assessments/text-assessments.service';
import { Submission } from 'app/entities/submission';

@Component({
    templateUrl: './text-assessment-dashboard.component.html',
    styles: [],
})
export class TextAssessmentDashboardComponent implements OnInit {
    exercise: TextExercise;
    submissions: TextSubmission[] = [];
    filteredSubmissions: TextSubmission[] = [];
    busy = false;
    predicate = 'id';
    reverse = false;

    private cancelConfirmationText: string;

    constructor(
        private route: ActivatedRoute,
        private exerciseService: ExerciseService,
        private textSubmissionService: TextSubmissionService,
        private assessmentsService: TextAssessmentsService,
        private momentDiff: DifferencePipe,
        private translateService: TranslateService,
    ) {
        translateService.get('artemisApp.textAssessment.confirmCancel').subscribe(text => (this.cancelConfirmationText = text));
    }

    async ngOnInit() {
        this.busy = true;
        const exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        this.exerciseService
            .find(exerciseId)
            .map(exerciseResponse => {
                if (exerciseResponse.body!.type !== ExerciseType.TEXT) {
                    throw new Error('Cannot use Text Assessment Dashboard with non-text Exercise type.');
                }

                return <TextExercise>exerciseResponse.body!;
            })
            .subscribe(exercise => {
                this.exercise = exercise;
                this.getSubmissions();
            });
    }

    private getSubmissions(): void {
        this.textSubmissionService
            .getTextSubmissionsForExercise(this.exercise.id, { submittedOnly: true })
            .map((response: HttpResponse<TextSubmission[]>) =>
                response.body!.map((submission: TextSubmission) => {
                    if (submission.result) {
                        // reconnect some associations
                        submission.result.submission = submission;
                        submission.result.participation = submission.participation;
                        submission.participation.results = [submission.result];
                    }

                    return submission;
                }),
            )
            .subscribe((submissions: TextSubmission[]) => {
                this.submissions = submissions;
                this.filteredSubmissions = submissions;
                this.busy = false;
            });
    }

    updateFilteredSubmissions(filteredSubmissions: Submission[]) {
        this.filteredSubmissions = filteredSubmissions as TextSubmission[];
    }

    public durationString(completionDate: Date, initializationDate: Date) {
        return this.momentDiff.transform(completionDate, initializationDate, 'minutes');
    }

    public assessmentTypeTranslationKey(result?: Result): string {
        if (result) {
            return `artemisApp.AssessmentType.${result.assessmentType}`;
        }
        return 'artemisApp.AssessmentType.null';
    }

    /**
     * Cancel the current assessment and reload the submissions to reflect the change.
     */
    cancelAssessment(submission: Submission) {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        if (confirmCancel) {
            this.assessmentsService.cancelAssessment(this.exercise.id, submission.id).subscribe(() => {
                this.getSubmissions();
            });
        }
    }
}
