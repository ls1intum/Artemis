import { Component, computed, inject, input, output, viewChild } from '@angular/core';
import { TextBlockRef } from 'app/text/shared/entities/text-block-ref.model';
import { TextBlockFeedbackEditorComponent } from 'app/text/manage/assess/textblock-feedback-editor/text-block-feedback-editor.component';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { GradingInstructionSelectionService } from 'app/exercise/structured-grading-criterion/grading-instruction-selection.service';
import { TextAssessmentEventType } from 'app/text/shared/entities/text-assesment-event.model';
import { FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { TextBlockType } from 'app/text/shared/entities/text-block.model';
import { TextAssessmentAnalytics } from 'app/text/manage/assess/analytics/text-assessment-analytics.service';
import { ActivatedRoute } from '@angular/router';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumUiButtonDirective } from '@tumaet/ui-angular';

type OptionalTextBlockRef = TextBlockRef | undefined;

@Component({
    selector: 'jhi-text-block-assessment-card',
    templateUrl: './text-block-assessment-card.component.html',
    styleUrls: ['./text-block-assessment-card.component.scss'],
    imports: [TextBlockFeedbackEditorComponent, ArtemisTranslatePipe, TranslateDirective, TumUiButtonDirective],
})
export class TextBlockAssessmentCardComponent {
    private route = inject(ActivatedRoute);
    private structuredGradingCriterionService = inject(StructuredGradingCriterionService);
    private readonly selectionService = inject(GradingInstructionSelectionService);
    private textAssessmentAnalytics = inject(TextAssessmentAnalytics);

    textBlockRef = input.required<TextBlockRef>();
    selected = input<boolean>(false);
    readOnly = input<boolean>(false);
    highlightDifferences = input<boolean>(false);
    criteria = input<GradingCriterion[]>();

    didSelect = output<OptionalTextBlockRef>();
    didChange = output<TextBlockRef>();
    didDelete = output<TextBlockRef>();
    feedbackEditor = viewChild.required(TextBlockFeedbackEditorComponent);

    /** Shows the apply-armed-instruction control while an instruction is armed. */
    protected readonly isKeyboardDropTarget = computed(() => !this.readOnly() && !!this.textBlockRef().selectable && this.selectionService.hasArmedInstruction());

    constructor() {
        this.textAssessmentAnalytics.setComponentRoute(this.route);
    }

    /**
     * Select a text block
     * @param {boolean} autofocus - Enable autofocus (defaults to true)
     */
    select(autofocus = true): void {
        if (this.readOnly()) {
            return;
        }
        const textBlockRef = this.textBlockRef();
        if (textBlockRef && !textBlockRef.selectable) {
            return;
        }

        this.didSelect.emit(textBlockRef);
        textBlockRef.initFeedback();

        if (autofocus) {
            setTimeout(() => this.feedbackEditor().focus());
            if (!this.selected() && textBlockRef.feedback?.type === FeedbackType.MANUAL) {
                this.textAssessmentAnalytics.sendAssessmentEvent(TextAssessmentEventType.ADD_FEEDBACK_AUTOMATICALLY_SELECTED_BLOCK, FeedbackType.MANUAL, TextBlockType.AUTOMATIC);
            }
        }
    }

    /**
     * Unselect a text block
     */
    unselect(): void {
        this.didSelect.emit(undefined);
        const textBlockRef = this.textBlockRef();
        delete textBlockRef.feedback;
        if (textBlockRef.block!.type === TextBlockType.MANUAL && textBlockRef.deletable) {
            this.didDelete.emit(textBlockRef);
        }
        this.feedbackDidChange();
    }

    /**
     * Hook to indicate that feedback did change
     */
    feedbackDidChange(): void {
        this.didChange.emit(this.textBlockRef());
    }

    /**
     * Connects the structured grading instructions with the feedback of a text block
     * @param {Event} event - The drop event
     */
    connectStructuredGradingInstructionsWithTextBlock(event: Event) {
        const textBlockRef = this.textBlockRef();
        if (!textBlockRef.selectable) {
            return;
        }
        this.select();
        if (textBlockRef.feedback) {
            this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(textBlockRef.feedback, event);
        }
        this.feedbackDidChange();
    }

    /**
     * Applies a previously armed instruction to this block's feedback.
     * Selects / initializes the block first so an unselected span without feedback can still receive the instruction.
     */
    applyArmedInstruction(): void {
        if (!this.isKeyboardDropTarget()) {
            return;
        }
        const textBlockRef = this.textBlockRef();
        this.select();
        if (!textBlockRef.feedback) {
            return;
        }
        if (!this.structuredGradingCriterionService.applyArmedInstructionToFeedback(textBlockRef.feedback)) {
            return;
        }
        this.feedbackDidChange();
    }
}
