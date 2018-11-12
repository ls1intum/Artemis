import { Component, EventEmitter, Output } from '@angular/core';
import { SelectionRectangle, TextSelectEvent } from 'app/text/tutor/text-assessment-editor/text-select.directive';

@Component({
    selector: 'jhi-text-assessment-editor',
    templateUrl: './text-assessment-editor.component.html',
    styleUrls: ['./text-assessment-editor.component.scss']
})
export class TextAssessmentEditorComponent {
    public hostRectangle: SelectionRectangle;
    @Output()
    public assessedText = new EventEmitter<string>();
    private selectedText: string;

    constructor() {}

    didSelectSolutionText(event: TextSelectEvent): void {
        // If a new selection has been created, the viewport and host rectangles will
        // exist. Or, if a selection is being removed, the rectangles will be null.
        if (event.hostRectangle) {
            this.hostRectangle = event.hostRectangle;
            this.selectedText = event.text;
        } else {
            this.hostRectangle = null;
            this.selectedText = null;
        }
    }

    deselectText(): void {
        document.getSelection().removeAllRanges();
        this.hostRectangle = null;
        this.selectedText = null;
    }

    assessSelection(): void {
        if (this.selectedText.trim().length === 0) {
            return;
        }

        this.assessedText.emit(this.selectedText.trim());
        this.deselectText();
    }
}
