import { Component, Input } from '@angular/core';
import { TextSubmission } from 'app/entities/text/text-submission.model';
import { Exercise } from 'app/entities/exercise.model';
import { TextEditorComponent } from '../../../../../exercises/text/participate/text-editor.component';

@Component({
    selector: 'jhi-text-exam-summary',
    templateUrl: './text-exam-summary.component.html',
    imports: [TextEditorComponent],
})
export class TextExamSummaryComponent {
    @Input() exercise: Exercise;
    @Input() submission: TextSubmission;
    @Input() expandProblemStatement?: boolean = false;
    @Input() isAfterResultsArePublished?: boolean = false;
}
