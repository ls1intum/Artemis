import { Component, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { TextBlockRef } from 'app/entities/text-block-ref.model';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess-new/textblock-feedback-editor/textblock-feedback-editor.component';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { TextBlockType } from 'app/entities/text-block.model';
import { FeedbackConflictType } from 'app/entities/feedback-conflict';

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
    @Input() isConflictingFeedback: boolean;
    @Input() conflictMode: boolean;
    @Input() conflictType?: FeedbackConflictType;
    @Input() isLeftConflictingFeedback: boolean;
    @Output() didSelect = new EventEmitter<OptionalTextBlockRef>();
    @Output() didChange = new EventEmitter<TextBlockRef>();
    @Output() didDelete = new EventEmitter<TextBlockRef>();
    @Output() onConflictsClicked = new EventEmitter<number>();
    @ViewChild(TextblockFeedbackEditorComponent) feedbackEditor: TextblockFeedbackEditorComponent;
    disableEditScore = false;

    private get isSelectableConflict(): boolean {
        return this.conflictMode && this.isConflictingFeedback && !this.isLeftConflictingFeedback;
    }

    constructor(public structuredGradingCriterionService: StructuredGradingCriterionService) {}

    /**
     * Select a text block
     * If it is conflict mode and this text block is already selected, then send null block to unselect it.
     * @param {boolean} autofocus - Enable autofocus (defaults to true)
     */
    select(autofocus = true): void {
        if (this.readOnly && !this.isSelectableConflict) {
            return;
        }

        if (this.isSelectableConflict && this.selected) {
            this.didSelect.emit(null);
            return;
        }

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
        if (this.textBlockRef.block!.type === TextBlockType.MANUAL) {
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
