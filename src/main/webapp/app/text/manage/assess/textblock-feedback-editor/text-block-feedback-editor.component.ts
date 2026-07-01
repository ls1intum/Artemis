import { Component, inject, input, output, viewChild } from '@angular/core';
import { TextBlock } from 'app/text/shared/entities/text-block.model';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute } from '@angular/router';
import { TextAssessmentEventType } from 'app/text/shared/entities/text-assesment-event.model';
import { TextAssessmentAnalytics } from 'app/text/manage/assess/analytics/text-assessment-analytics.service';
import { faAngleRight } from '@fortawesome/free-solid-svg-icons';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TextblockFeedbackDropdownComponent } from './dropdown/textblock-feedback-dropdown.component';
import { UnifiedFeedbackComponent } from 'app/shared/components/unified-feedback/unified-feedback.component';

@Component({
    selector: 'jhi-text-block-feedback-editor',
    templateUrl: './text-block-feedback-editor.component.html',
    styleUrls: ['./text-block-feedback-editor.component.scss'],
    imports: [UnifiedFeedbackComponent, NgbDropdown, NgbDropdownToggle, NgbDropdownMenu, TextblockFeedbackDropdownComponent, FaIconComponent],
})
export class TextBlockFeedbackEditorComponent {
    private route = inject(ActivatedRoute);
    private structuredGradingCriterionService = inject(StructuredGradingCriterionService);
    private textAssessmentAnalytics = inject(TextAssessmentAnalytics);

    textBlock = input<TextBlock>(new TextBlock());
    feedback = input<Feedback>(new Feedback());
    feedbackChange = output<Feedback>();
    onClose = output<void>();
    onFocus = output<void>();
    readOnly = input<boolean>(false);
    highlightDifferences = input<boolean>(false);
    criteria = input<GradingCriterion[]>();

    private readonly unifiedFeedback = viewChild.required(UnifiedFeedbackComponent);

    faAngleRight = faAngleRight;

    constructor() {
        this.textAssessmentAnalytics.setComponentRoute(this.route);
    }

    /**
     * Dismiss changes in feedback editor
     */
    dismiss(): void {
        this.onClose.emit();
        this.textAssessmentAnalytics.sendAssessmentEvent(TextAssessmentEventType.DELETE_FEEDBACK, this.feedback().type, this.textBlock().type);
    }

    /**
     * Hook to indicate pressed Escape key
     */
    escKeyup(): void {
        this.unifiedFeedback().toggleDeleteConfirm();
    }

    /**
     * Set focus to the text area
     */
    focus(): void {
        this.unifiedFeedback().focusTextarea();
    }

    /**
     * Hook to indicate a score change; resets the correction status because it is now stale
     */
    onScoreChange(): void {
        this.feedback().correctionStatus = undefined;
        this.didChange();
    }

    /**
     * Hook to indicate changes in the feedback editor
     */
    didChange(): void {
        const feedbackValue = this.feedback();
        const feedbackTextBefore = feedbackValue.text;
        Feedback.updateFeedbackTypeOnChange(feedbackValue);
        this.feedbackChange.emit(feedbackValue);
        // send event to analytics if the feedback was adapted (=> title text changes to have prefix with "adapted" in it)
        if (feedbackTextBefore !== feedbackValue.text) {
            this.textAssessmentAnalytics.sendAssessmentEvent(TextAssessmentEventType.EDIT_AUTOMATIC_FEEDBACK, feedbackValue.type, this.textBlock().type);
        }
    }

    connectFeedbackWithInstruction(event: Event) {
        const feedbackValue = this.feedback();
        this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(feedbackValue, event);

        // Reset the feedback correction status upon setting grading instruction in order to hide it.
        feedbackValue.correctionStatus = undefined;

        this.didChange();
    }
}
