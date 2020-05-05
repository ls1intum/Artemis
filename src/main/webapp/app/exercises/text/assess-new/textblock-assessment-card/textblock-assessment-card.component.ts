import { Component, Input, Output, EventEmitter, ViewChild } from '@angular/core';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess-new/textblock-feedback-editor/textblock-feedback-editor.component';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';

type OptionalTextBlockRef = TextBlockRef | null;

@Component({
    selector: 'jhi-textblock-assessment-card',
    templateUrl: './textblock-assessment-card.component.html',
    styleUrls: ['./textblock-assessment-card.component.scss'],
})
export class TextblockAssessmentCardComponent {
    @Input() textBlockRef: TextBlockRef;
    @Input() selected = false;
    @Output() didSelect = new EventEmitter<OptionalTextBlockRef>();
    @Output() didChange = new EventEmitter<TextBlockRef>();
    @ViewChild(TextblockFeedbackEditorComponent) feedbackEditor: TextblockFeedbackEditorComponent;

    constructor(public structuredGradingCriterionService: StructuredGradingCriterionService) {}

    /**
     * Select a text block
     * @param {boolean} autofocus - Enable autofocus (defaults to true)
     */
    select(autofocus = true): void {
        this.didSelect.emit(this.textBlockRef);
        this.textBlockRef.initFeedback();

        if (autofocus) {
            setTimeout(() => this.feedbackEditor.focus());
        }
    }

    /**
     * Unselect a text block
     */
    unselect(): void {
        this.didSelect.emit(null);
        delete this.textBlockRef.feedback;
        this.feedbackDidChange();
    }

    /**
     * Hook to indicate that feedback did change
     */
    feedbackDidChange(): void {
        this.didChange.emit(this.textBlockRef);
    }

    /**
     * Connects the structured grading instructions with the feedback of a text block
     * @param {Event} event - The drop event
     */
    connectStructuredGradingInstructionsWithTextBlock(event: Event) {
        this.select();
        if (this.textBlockRef.feedback) {
            this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(this.textBlockRef.feedback, event);
        }
        this.feedbackDidChange();
    }
}
