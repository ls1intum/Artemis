import { Component, input } from '@angular/core';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingSubmissionComponent } from 'app/exercises/modeling/participate/modeling-submission.component';

@Component({
    selector: 'jhi-modeling-exam-summary',
    templateUrl: './modeling-exam-summary.component.html',
    imports: [ModelingSubmissionComponent],
})
export class ModelingExamSummaryComponent {
    exercise = input.required<ModelingExercise>();
    submission = input.required<ModelingSubmission>();
    isPrinting = input(false);
    expandProblemStatement = input(false);
    isAfterResultsArePublished = input(false);
}
