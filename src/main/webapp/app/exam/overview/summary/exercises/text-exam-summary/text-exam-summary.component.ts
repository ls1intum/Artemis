import { Component, input } from '@angular/core';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextEditorComponent } from 'app/text/overview/text-editor/text-editor.component';

@Component({
    selector: 'jhi-text-exam-summary',
    templateUrl: './text-exam-summary.component.html',
    imports: [TextEditorComponent],
})
export class TextExamSummaryComponent {
    readonly exercise = input<Exercise>(undefined!);
    readonly submission = input<TextSubmission>(undefined!);
    readonly expandProblemStatement = input(false);
    readonly isAfterResultsArePublished = input(false);
}
