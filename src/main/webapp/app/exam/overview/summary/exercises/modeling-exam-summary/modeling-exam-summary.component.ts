import { Component, input } from '@angular/core';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingSubmissionComponent } from 'app/modeling/overview/modeling-submission/modeling-submission.component';

@Component({
    selector: 'jhi-modeling-exam-summary',
    templateUrl: './modeling-exam-summary.component.html',
    imports: [ModelingSubmissionComponent],
})
export class ModelingExamSummaryComponent {
    readonly exercise = input<ModelingExercise>(undefined!);
    readonly submission = input<ModelingSubmission>(undefined!);
    readonly isPrinting = input(false);
    readonly expandProblemStatement = input(false);
    readonly isAfterResultsArePublished = input(false);
}
