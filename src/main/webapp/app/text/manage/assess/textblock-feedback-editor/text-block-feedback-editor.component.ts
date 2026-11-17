import { Component, computed, effect, inject, input, output, viewChild } from '@angular/core';
import { TextBlock } from 'app/text/shared/entities/text-block.model';
import { FEEDBACK_SUGGESTION_ACCEPTED_IDENTIFIER, FEEDBACK_SUGGESTION_ADAPTED_IDENTIFIER, Feedback, FeedbackSuggestionType } from 'app/assessment/shared/entities/feedback.model';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { TumAetUiButtonDirective, TumAetUiMenuComponent, TumAetUiMenuItemDirective, TumAetUiMenuTriggerDirective } from '@tumaet/ui-angular';
import { ActivatedRoute } from '@angular/router';
import { TextAssessmentEventType } from 'app/text/shared/entities/text-assesment-event.model';
import { TextAssessmentAnalytics } from 'app/text/manage/assess/analytics/text-assessment-analytics.service';
import { faCaretDown } from '@fortawesome/free-solid-svg-icons';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { UnifiedFeedbackComponent } from 'app/shared/components/unified-feedback/unified-feedback.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-text-block-feedback-editor',
    templateUrl: './text-block-feedback-editor.component.html',
    imports: [
        UnifiedFeedbackComponent,
        TumAetUiButtonDirective,
        TumAetUiMenuComponent,
        TumAetUiMenuItemDirective,
        TumAetUiMenuTriggerDirective,
        HelpIconComponent,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
    ],
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

    faCaretDown = faCaretDown;

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
    applyArmedInstruction(): void {
        if (!this.readOnly() && this.structuredGradingCriterionService.applyArmedInstructionToFeedback(this.feedback())) {
            this.onScoreChange();
        }
    }

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

    /**
     * Connects the feedback with the structured grading instruction that was chosen in the menu.
     * @param instruction the chosen grading instruction
     */
    updateAssessmentWithDropdown(instruction: GradingInstruction): void {
        const feedbackValue = this.feedback();
        feedbackValue.gradingInstruction = instruction;
        feedbackValue.credits = instruction.credits;

        // Reset the feedback correction status upon setting grading instruction in order to hide it.
        feedbackValue.correctionStatus = undefined;

        this.didChange();
    }

    /**
     * Get the color for grading instruction based on the credits of the instruction
     * @param instr the instruction object we get its color based on its credits
     */
    getInstrColour(instr: GradingInstruction): string | undefined {
        if (instr.credits === 0) {
            return 'var(--sgi-assessment-layout-zero-background)';
        } else if (instr.credits < 0) {
            return 'var(--sgi-assessment-layout-negative-background)';
        } else if (instr.credits > 0) {
            return 'var(--sgi-assessment-layout-positive-background)';
        }
        return undefined;
    }

    connectFeedbackWithInstruction(event: Event) {
        const feedbackValue = this.feedback();
        this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(feedbackValue, event);

        // Reset the feedback correction status upon setting grading instruction in order to hide it.
        feedbackValue.correctionStatus = undefined;

        this.didChange();
    }
}
