import { Component, EventEmitter, Input, Output, ViewChild, inject } from '@angular/core';
import { TextBlockRef } from 'app/entities/text/text-block-ref.model';
import { TextBlockFeedbackEditorComponent } from 'app/text/manage/assess/textblock-feedback-editor/text-block-feedback-editor.component';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { TextAssessmentEventType } from 'app/entities/text/text-assesment-event.model';
import { FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { TextBlockType } from 'app/entities/text/text-block.model';
import { TextAssessmentAnalytics } from 'app/text/manage/assess/analytics/text-assesment-analytics.service';
import { ActivatedRoute } from '@angular/router';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';

type OptionalTextBlockRef = TextBlockRef | undefined;

@Component({
    selector: 'jhi-text-block-assessment-card',
    templateUrl: './text-block-assessment-card.component.html',
    styleUrls: ['./text-block-assessment-card.component.scss'],
    imports: [TextBlockFeedbackEditorComponent],
})
export class TextBlockAssessmentCardComponent {
    private route = inject(ActivatedRoute);
    private structuredGradingCriterionService = inject(StructuredGradingCriterionService);
    private textAssessmentAnalytics = inject(TextAssessmentAnalytics);

    @Input() textBlockRef: TextBlockRef;
    @Input() selected = false;
    @Input() readOnly: boolean;
    @Input() isMissedFeedback: boolean;
    @Input() highlightDifferences: boolean;
    @Input() criteria?: GradingCriterion[];

    @Output() didSelect = new EventEmitter<OptionalTextBlockRef>();
    @Output() didChange = new EventEmitter<TextBlockRef>();
    @Output() didDelete = new EventEmitter<TextBlockRef>();
    @ViewChild(TextBlockFeedbackEditorComponent) feedbackEditor: TextBlockFeedbackEditorComponent;

    constructor() {
        this.textAssessmentAnalytics.setComponentRoute(this.route);
    }

    /**
     * Select a text block
     * @param {boolean} autofocus - Enable autofocus (defaults to true)
     */
    select(autofocus = true): void {
        if (this.readOnly) {
            return;
        }
        if (this.textBlockRef && !this.textBlockRef.selectable) {
            return;
        }

        this.didSelect.emit(this.textBlockRef);
        this.textBlockRef.initFeedback();

        if (autofocus) {
            setTimeout(() => this.feedbackEditor.focus());
            if (!this.selected && this.textBlockRef.feedback?.type === FeedbackType.MANUAL) {
                this.textAssessmentAnalytics.sendAssessmentEvent(TextAssessmentEventType.ADD_FEEDBACK_AUTOMATICALLY_SELECTED_BLOCK, FeedbackType.MANUAL, TextBlockType.AUTOMATIC);
            }
        }
    }

    /**
     * Unselect a text block
     */
    unselect(): void {
        this.didSelect.emit(undefined);
        delete this.textBlockRef.feedback;
        if (this.textBlockRef.block!.type === TextBlockType.MANUAL && this.textBlockRef.deletable) {
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
        if (!this.textBlockRef.selectable) {
            return;
        }
        this.select();
        if (this.textBlockRef.feedback) {
            this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(this.textBlockRef.feedback, event);
        }
        this.feedbackDidChange();
    }
}
