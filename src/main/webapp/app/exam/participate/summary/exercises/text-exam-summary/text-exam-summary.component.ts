import { Component, input } from '@angular/core';
import { TextSubmission } from 'app/entities/text/text-submission.model';
import { Exercise } from 'app/entities/exercise.model';
import { TextEditorComponent } from 'app/exercises/text/participate/text-editor.component';

@Component({
    selector: 'jhi-text-exam-summary',
    templateUrl: './text-exam-summary.component.html',
    imports: [TextEditorComponent],
})
export class TextExamSummaryComponent {
    exercise = input.required<Exercise>();
    submission = input.required<TextSubmission>();
    expandProblemStatement = input(false);
    isAfterResultsArePublished = input(false);
}
