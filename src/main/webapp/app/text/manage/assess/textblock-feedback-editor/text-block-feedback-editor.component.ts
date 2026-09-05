import { Component, computed, effect, inject, input, output, viewChild } from '@angular/core';
import { TextBlock } from 'app/text/shared/entities/text-block.model';
import { FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER, FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER, Feedback, FeedbackSuggestionType } from 'app/assessment/shared/entities/feedback.model';
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
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

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
    private artemisTranslatePipe = inject(ArtemisTranslatePipe);

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

    readonly connectToInstructionAriaLabel = computed(() => this.artemisTranslatePipe.transform('artemisApp.textAssessment.feedbackEditor.connectToInstruction'));

    /**
     * Suggestion type observed the last time the bound {@link feedback} reference changed, i.e. its value before
     * the assessor's current round of edits. Used by {@link didChange} to detect the one-time accepted->adapted
     * transition regardless of which mutation path triggered it.
     */
    private lastKnownSuggestionType: FeedbackSuggestionType = FeedbackSuggestionType.NO_SUGGESTION;

    constructor() {
        this.textAssessmentAnalytics.setComponentRoute(this.route);
        effect(() => {
            this.lastKnownSuggestionType = Feedback.getFeedbackSuggestionType(this.feedback());
        });
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
     * Hook to indicate changes in the feedback editor. Single entry point for every assessor mutation on this
     * feedback (unified title/detail/score inputs, rubric-dropdown selection, connectFeedbackWithInstruction),
     * so the accepted->adapted transition and its analytics event are centralized here rather than duplicated
     * per mutation path.
     */
    didChange(): void {
        const feedbackValue = this.feedback();
        // The unified inputs already rewrite an accepted suggestion's prefix to adapted themselves before this
        // runs; other mutation paths (grading-instruction connect/drop, rubric dropdown) don't, so do it here too.
        if (feedbackValue.text?.startsWith(FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER)) {
            feedbackValue.text = FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER + feedbackValue.text.slice(FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER.length);
        }

        const suggestionType = Feedback.getFeedbackSuggestionType(feedbackValue);
        const isFirstAdaptation = this.lastKnownSuggestionType === FeedbackSuggestionType.ACCEPTED && suggestionType === FeedbackSuggestionType.ADAPTED;
        this.lastKnownSuggestionType = suggestionType;

        this.feedbackChange.emit(feedbackValue);
        if (isFirstAdaptation) {
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
