import { Component, Input } from '@angular/core';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingSubmissionComponent } from 'app/modeling/overview/modeling-submission.component';

@Component({
    selector: 'jhi-modeling-exam-summary',
    templateUrl: './modeling-exam-summary.component.html',
    imports: [ModelingSubmissionComponent],
})
export class ModelingExamSummaryComponent {
    @Input() exercise: ModelingExercise;
    @Input() submission: ModelingSubmission;
    @Input() isPrinting = false;
    @Input() expandProblemStatement = false;
    @Input() isAfterResultsArePublished = false;
}
