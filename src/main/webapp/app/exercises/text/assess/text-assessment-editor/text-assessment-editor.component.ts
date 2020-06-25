import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Feedback } from 'app/entities/feedback.model';
import { TextBlock } from 'app/entities/text-block.model';

@Component({
    selector: 'jhi-text-assessment-editor',
    templateUrl: './text-assessment-editor.component.html',
    styleUrls: ['./text-assessment-editor.component.scss'],
})
export class TextAssessmentEditorComponent {
    @Input() public submissionText: string;
    @Input() public assessments: Feedback[];
    @Input() public blocks: (TextBlock | undefined)[];
    @Input() public disabled = false;
    @Output() public assessedText = new EventEmitter<string>();

    /**
     * Add an assessment to a selection.
     */
    assessSelection($text: string): void {
        if (this.disabled) {
            return;
        }

        if ($text.length === 0) {
            return;
        }

        this.assessedText.emit($text);
    }
}
