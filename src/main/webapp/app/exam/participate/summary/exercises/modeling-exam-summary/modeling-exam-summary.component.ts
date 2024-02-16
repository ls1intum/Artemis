import { Component, Input } from '@angular/core';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';

@Component({
    selector: 'jhi-modeling-exam-summary',
    templateUrl: './modeling-exam-summary.component.html',
})
export class ModelingExamSummaryComponent {
    @Input() exercise: ModelingExercise;
    @Input() submission: ModelingSubmission;
    @Input() isPrinting?: boolean = false;
    @Input() expandProblemStatement?: boolean = false;
    @Input() isAfterResultsArePublished?: boolean = false;
}
