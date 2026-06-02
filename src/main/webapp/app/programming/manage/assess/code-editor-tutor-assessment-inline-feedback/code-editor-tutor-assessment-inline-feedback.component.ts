import { Component, ElementRef, inject, input, linkedSignal, output, viewChild } from '@angular/core';
import { Feedback, FeedbackType, buildFeedbackTextForReview } from 'app/assessment/shared/entities/feedback.model';
import { FeedbackSuggestionBadgeComponent } from 'app/exercise/feedback/feedback-suggestion-badge/feedback-suggestion-badge.component';
import { ButtonSize } from 'app/shared-ui/components/buttons/button/button.component';
import { cloneDeep } from 'lodash-es';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { roundValueSpecifiedByCourseSettings } from 'app/foundation/util/utils';
import { Course } from 'app/course/shared/entities/course.model';
import { faBan, faExclamationTriangle, faPencilAlt, faQuestionCircle, faSave, faTrashAlt } from '@fortawesome/free-solid-svg-icons';
import { Subject } from 'rxjs';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { GradingInstructionLinkIconComponent } from 'app/shared-ui/grading-instruction-link-icon/grading-instruction-link-icon.component';
import { FormsModule } from '@angular/forms';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { AssessmentCorrectionRoundBadgeComponent } from 'app/assessment/manage/unreferenced-feedback-detail/assessment-correction-round-badge/assessment-correction-round-badge.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FeedbackContentPipe } from 'app/foundation/pipes/feedback-content.pipe';
import { QuotePipe } from 'app/foundation/pipes/quote.pipe';

@Component({
    selector: 'jhi-code-editor-tutor-assessment-inline-feedback',
    templateUrl: './code-editor-tutor-assessment-inline-feedback.component.html',
    imports: [
        FeedbackSuggestionBadgeComponent,
        TranslateDirective,
        FaIconComponent,
        NgbTooltip,
        GradingInstructionLinkIconComponent,
        FormsModule,
        DeleteButtonDirective,
        AssessmentCorrectionRoundBadgeComponent,
        ArtemisTranslatePipe,
        FeedbackContentPipe,
        QuotePipe,
    ],
})
export class CodeEditorTutorAssessmentInlineFeedbackComponent {
    protected readonly faSave = faSave;
    protected readonly faBan = faBan;
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faTrashAlt = faTrashAlt;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly Feedback = Feedback;
    protected readonly ButtonSize = ButtonSize;
    protected readonly MANUAL = FeedbackType.MANUAL;

    // Expose the function to the template
    protected readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;

    private structuredGradingCriterionService = inject(StructuredGradingCriterionService);
    // Needed for the outer editor to access the DOM node of this component
    public elementRef = inject(ElementRef);

    readonly feedback = input<Feedback>();

    /**
     * The feedback currently displayed/edited. It is seeded from the {@link feedback} input (defaulting to a fresh
     * {@link Feedback} when none is provided) and can be reassigned internally (e.g. when the user cancels an edit).
     * Using a {@link linkedSignal} preserves the original setter behavior: whenever the bound input changes, the
     * working copy resets to the new value.
     */
    readonly currentFeedback = linkedSignal<Feedback>(() => this.feedback() ?? new Feedback());

    readonly selectedFile = input.required<string>();

    readonly codeLine = input.required<number>();

    readonly readOnly = input.required<boolean>();
    readonly highlightDifferences = input<boolean>();
    readonly course = input<Course>();
    readonly textareaRef = viewChild<ElementRef>('detailText');

    readonly onUpdateFeedback = output<Feedback>();
    readonly onCancelFeedback = output<number>();
    readonly onDeleteFeedback = output<Feedback>();
    readonly onEditFeedback = output<number>();

    /**
     * Whether the feedback is rendered in read-only mode. Mirrors the original setter behavior: it is `true` whenever a
     * feedback was bound via the input and resets accordingly when the input changes.
     */
    readonly viewOnly = linkedSignal<boolean>(() => !!this.feedback());

    /**
     * Snapshot of the feedback used to restore state when the user cancels an edit. Reset whenever the input changes.
     */
    readonly oldFeedback = linkedSignal<Feedback>(() => cloneDeep(this.feedback() ?? new Feedback()));

    private dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    /**
     * Updates the current feedback and sets props and emits the feedback to parent component
     */
    updateFeedback() {
        const feedback = this.currentFeedback();
        feedback.type = this.MANUAL;
        feedback.reference = `file:${this.selectedFile()}_line:${this.codeLine()}`;
        if (Feedback.isFeedbackSuggestion(feedback)) {
            Feedback.updateFeedbackTypeOnChange(feedback);
        } else {
            feedback.text = `File ${this.selectedFile()} at line ${this.codeLine() + 1}`;
        }
        this.viewOnly.set(true);
        if (feedback.credits && feedback.credits > 0) {
            feedback.positive = true;
        }
        this.onUpdateFeedback.emit(feedback);
    }

    /**
     * When an inline feedback already exists, we set it back and display it the viewOnly mode.
     * Otherwise, the component is not displayed anymore in the parent component
     */
    cancelFeedback() {
        const restored = this.oldFeedback();
        this.currentFeedback.set(restored);
        this.oldFeedback.set(cloneDeep(restored));
        this.viewOnly.set(restored.type === this.MANUAL);
        this.onCancelFeedback.emit(this.codeLine());
    }

    /**
     * Deletes feedback after confirmation and emits to parent component
     */
    deleteFeedback() {
        this.onDeleteFeedback.emit(this.currentFeedback());
        this.dialogErrorSource.next('');
    }

    /**
     * Checks if component is in view mode and focuses feedback text area
     * @param line Line of code which is emitted to the parent
     */
    editFeedback(line: number) {
        this.viewOnly.set(false);
        // Save the old feedback in case the user cancels later
        this.oldFeedback.set(cloneDeep(this.currentFeedback()));
        this.onEditFeedback.emit(line);
        setTimeout(() => (this.textareaRef()?.nativeElement as HTMLTextAreaElement | undefined)?.focus());
    }

    /**
     * Updates the feedback with data of Structured Grading Instructions (SGI)
     * @param event Drop event with SGI data
     */
    updateFeedbackOnDrop(event: Event) {
        const feedback = this.currentFeedback();
        this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(feedback, event);
        feedback.reference = `file:${this.selectedFile()}_line:${this.codeLine()}`;
        feedback.text = `File ${this.selectedFile()} at line ${this.codeLine() + 1}`;
    }

    /**
     * Builds the feedback text. When the feedback has a link with grading instruction it merges the feedback of
     * the grading instruction with the feedback text provided by the assessor.
     *
     * @param feedback The feedback for which the text visible to the user should be created.
     * @returns The formatted string representing the feedback text ready to display.
     */
    public buildFeedbackTextForCodeEditor(feedback: Feedback): string {
        return buildFeedbackTextForReview(feedback, false);
    }

    /**
     * This method prevents the propagation to global event listeners (especially the monaco event listener), so the backspace key can be used.
     *
     * As this component is rendered within the monaco code editor, the monaco keydown event listener is attached to input fields
     * in this component.
     * In the assessment the code editor is readonly, so it will prevent the default behavior of the backspace key.
     *
     * To verify that the assumption of the side effects of the monaco code editor do still hold, use Chromes developer tools:
     * 1. Inspect the textarea element
     * 2. Go to the Event Listeners pane
     * 3. Expand the keydown events to see which functions are bound to these events
     * 4. Check if the monaco editor is bound to the keydown event and causes the issue when not using the handleKeydown method
     * 5. You should observe, that when deleting the monaco event listener and NOT using the handleKeydown method, the backspace key works as expected
     */
    protected handleKeydown(event: KeyboardEvent) {
        if (event.key === 'Backspace') {
            event.stopPropagation();
        }
    }
}
