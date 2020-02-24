import { Component, EventEmitter, Input, Output } from '@angular/core';
import { SelectionRectangle, TextSelectEvent } from './text-select.directive';
import { Feedback } from 'app/entities/feedback.model';
import { TextBlock } from 'app/entities/text-block.model';

@Component({
    selector: 'jhi-text-assessment-editor',
    templateUrl: './text-assessment-editor.component.html',
    styleUrls: ['./text-assessment-editor.component.scss'],
})
export class TextAssessmentEditorComponent {
    public hostRectangle: SelectionRectangle | null;
    @Input() public submissionText: string;
    @Input() public assessments: Feedback[];
    @Input() public blocks: (TextBlock | undefined)[];
    @Input() public disabled = false;
    @Output() public assessedText = new EventEmitter<string>();
    private selectedText: string | null;

    didSelectSolutionText(event: TextSelectEvent): void {
        if (this.disabled) {
            return;
        }

        // If a new selection has been created, the viewport and host rectangles will
        // exist. Or, if a selection is being removed, the rectangles will be null.
        if (event.hostRectangle) {
            this.hostRectangle = event.hostRectangle;
            this.selectedText = event.text.replace(/(?:\r\n|\r|\n)/g, '<br>');
        } else {
            this.hostRectangle = null;
            this.selectedText = null;
        }
    }

    deselectText(): void {
        document.getSelection()!.removeAllRanges();
        this.hostRectangle = null;
        this.selectedText = null;
    }

    assessSelection(): void {
        if (this.disabled || this.selectedText == null) {
            return;
        }

        if (this.selectedText.trim().length === 0) {
            return;
        }

        this.assessedText.emit(this.selectedText.trim());
        this.deselectText();
    }
}
