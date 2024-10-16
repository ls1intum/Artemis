import { Component, EventEmitter, Input, Output, ViewChild, inject } from '@angular/core';
import { TextBlockRef } from 'app/entities/text/text-block-ref.model';
import { TextblockFeedbackEditorComponent } from 'app/exercises/text/assess/textblock-feedback-editor/textblock-feedback-editor.component';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { TextAssessmentEventType } from 'app/entities/text/text-assesment-event.model';
import { FeedbackType } from 'app/entities/feedback.model';
import { TextBlockType } from 'app/entities/text/text-block.model';
import { TextAssessmentAnalytics } from 'app/exercises/text/assess/analytics/text-assesment-analytics.service';
import { ActivatedRoute } from '@angular/router';
import { GradingCriterion } from 'app/exercises/shared/structured-grading-criterion/grading-criterion.model';

type OptionalTextBlockRef = TextBlockRef | undefined;

@Component({
    selector: 'jhi-textblock-assessment-card',
    templateUrl: './textblock-assessment-card.component.html',
    styleUrls: ['./textblock-assessment-card.component.scss'],
})
export class TextblockAssessmentCardComponent {
    structuredGradingCriterionService = inject(StructuredGradingCriterionService);
    textAssessmentAnalytics = inject(TextAssessmentAnalytics);
    protected route = inject(ActivatedRoute);

    @Input() textBlockRef: TextBlockRef;
    @Input() selected = false;
    @Input() readOnly: boolean;
    @Input() isMissedFeedback: boolean;
    @Input() highlightDifferences: boolean;
    @Input() criteria?: GradingCriterion[];

    @Output() didSelect = new EventEmitter<OptionalTextBlockRef>();
    @Output() didChange = new EventEmitter<TextBlockRef>();
    @Output() didDelete = new EventEmitter<TextBlockRef>();
    @ViewChild(TextblockFeedbackEditorComponent) feedbackEditor: TextblockFeedbackEditorComponent;

    constructor() {
        const textAssessmentAnalytics = this.textAssessmentAnalytics;
        const route = this.route;

        textAssessmentAnalytics.setComponentRoute(route);
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
