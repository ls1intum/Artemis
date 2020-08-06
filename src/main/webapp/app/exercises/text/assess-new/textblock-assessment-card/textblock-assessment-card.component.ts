import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess-new/textblock-feedback-editor/textblock-feedback-editor.component';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { TextBlockType } from 'app/entities/text-block.model';

type OptionalTextBlockRef = TextBlockRef | null;

@Component({
    selector: 'jhi-textblock-assessment-card',
    templateUrl: './textblock-assessment-card.component.html',
    styleUrls: ['./textblock-assessment-card.component.scss'],
})
export class TextblockAssessmentCardComponent {
    @Input() textBlockRef: TextBlockRef;
    @Input() selected = false;
    @Input() readOnly: boolean;
    @Output() didSelect = new EventEmitter<OptionalTextBlockRef>();
    @Output() didChange = new EventEmitter<TextBlockRef>();
    @Output() didDelete = new EventEmitter<TextBlockRef>();
    @ViewChild(TextblockFeedbackEditorComponent) feedbackEditor: TextblockFeedbackEditorComponent;
    disableEditScore = false;

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
        if (this.textBlockRef.block.type === TextBlockType.MANUAL) {
            this.didDelete.emit(this.textBlockRef);
        }
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
            if (this.textBlockRef.feedback.gradingInstruction && this.textBlockRef.feedback.gradingInstruction.usageCount !== 0) {
                this.disableEditScore = true;
            } else {
                this.disableEditScore = false;
            }
        }
        this.feedbackDidChange();
    }
}
