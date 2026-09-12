import { Component, ElementRef, computed, inject, input, linkedSignal, output, viewChild } from '@angular/core';
import { Feedback, FeedbackType, buildFeedbackTextForReview } from 'app/assessment/shared/entities/feedback.model';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { Course } from 'app/course/shared/entities/course.model';
import { faPencilAlt, faSave } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { UnifiedFeedbackComponent } from 'app/shared/components/unified-feedback/unified-feedback.component';
import { deepClone } from 'app/foundation/util/deep-clone.util';

@Component({
    selector: 'jhi-code-editor-tutor-assessment-inline-feedback',
    templateUrl: './code-editor-tutor-assessment-inline-feedback.component.html',
    styleUrl: './code-editor-tutor-assessment-inline-feedback.component.scss',
    imports: [TranslateDirective, FaIconComponent, UnifiedFeedbackComponent],
    // Monaco anchors the widget on this component's own host element (see `elementRef` below), so the id/width it
    // needs live on the host directly instead of behind an extra wrapping div in the template.
    host: {
        '[attr.id]': "'code-editor-inline-feedback-' + codeLine()",
        '[style.max-width.%]': '95',
    },
})
export class CodeEditorTutorAssessmentInlineFeedbackComponent {
    protected readonly faSave = faSave;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly Feedback = Feedback;
    protected readonly MANUAL = FeedbackType.MANUAL;

    private structuredGradingCriterionService = inject(StructuredGradingCriterionService);
    // Needed for the outer editor to access the DOM node of this component
    public elementRef = inject(ElementRef);

    readonly feedback = input<Feedback>();

    /**
     * The feedback currently displayed/edited. It is seeded from the {@link feedback} input (defaulting to a fresh
     * {@link Feedback} when none is provided) and can be reassigned internally (e.g. when the user cancels an edit).
     * Using a {@link linkedSignal} preserves the original setter behavior: whenever the bound input changes, the
     * working copy resets to the new value.
     *
     * Manual/new feedback is fully owned by the tutor writing this assessment, so it is edited through the exact
     * object bound via {@link feedback}, matching the live, auto-committing flow in {@link onFieldChanged}. Any
     * other type (e.g. automatic/static-analysis) still shares that same object with {@link automaticFeedback} in
     * the container until an explicit save, so editing it in place would leak in-progress field changes into the
     * assessment even if the edit is later dismissed, and would duplicate the feedback into both the automatic and
     * referenced buckets once {@link commitFeedback} retags it as manual. A working clone keeps those edits local
     * until {@link updateFeedback} commits it.
     */
    readonly currentFeedback = linkedSignal<Feedback>(() => {
        const feedback = this.feedback();
        if (!feedback) {
            return new Feedback();
        }
        return feedback.type === undefined || feedback.type === this.MANUAL ? feedback : deepClone(feedback);
    });

    readonly selectedFile = input.required<string>();

    readonly codeLine = input.required<number>();

    readonly readOnly = input.required<boolean>();
    readonly highlightDifferences = input<boolean>(false);
    readonly course = input<Course>();
    private readonly unifiedFeedback = viewChild(UnifiedFeedbackComponent);

    readonly onUpdateFeedback = output<Feedback>();
    readonly onCancelFeedback = output<number>();
    readonly onDeleteFeedback = output<Feedback>();
    readonly onEditFeedback = output<number>();

    /**
     * Whether the bound feedback is manual or not yet typed (no type at all, i.e. a freshly added line). Both are
     * fully owned by the tutor writing this assessment, so they get the always-open, auto-committing editor. Any
     * other type (e.g. automatic/static-analysis feedback) keeps the legacy collapsed-view-plus-explicit-save flow,
     * since editing it is a secondary, easy-to-get-wrong action that should not happen by accident.
     */
    protected readonly isNewOrManual = computed(() => {
        const type = this.feedback()?.type;
        return type === undefined || type === this.MANUAL;
    });

    /**
     * Whether the feedback is rendered collapsed. Manual/new feedback is never collapsed while editable (it has no
     * explicit save step to collapse it); any other type still starts collapsed and is only opened via
     * {@link editFeedback}.
     */
    readonly viewOnly = linkedSignal<boolean>(() => this.readOnly() || !this.isNewOrManual());

    /**
     * Snapshot used to restore a non-manual feedback (e.g. automatic/static-analysis) if its in-progress edit is
     * dismissed. Reset whenever the input changes.
     */
    readonly oldFeedback = linkedSignal<Feedback>(() => deepClone(this.feedback() ?? new Feedback()));

    /**
     * The auto-generated title for a manually created (non-suggestion) inline feedback. Computed live so it already
     * reflects the current file/line while the feedback is being edited, not only after {@link commitFeedback}
     * writes the same string into `feedback.text` on commit - otherwise the title falls back to the generic,
     * points-derived placeholder for that in-between period.
     */
    protected readonly derivedTitle = computed(() => `File ${this.selectedFile()} at line ${this.codeLine() + 1}`);

    /**
     * Finalizes and emits the current feedback: assigns its reference and derived title (unless it is an
     * already-accepted suggestion, whose title is the suggestion's own) and marks it positive when it awards credit.
     */
    private commitFeedback(): void {
        const feedback = this.currentFeedback();
        feedback.type = this.MANUAL;
        feedback.reference = `file:${this.selectedFile()}_line:${this.codeLine()}`;
        if (!Feedback.isFeedbackSuggestion(feedback)) {
            feedback.text = `File ${this.selectedFile()} at line ${this.codeLine() + 1}`;
        }
        if (feedback.credits && feedback.credits > 0) {
            feedback.positive = true;
        }
        this.onUpdateFeedback.emit(feedback);
    }

    /**
     * Auto-commits a manual (or brand new) feedback on every title/detail/credits change, the same live-save
     * behavior as unreferenced feedback - no separate save step. A non-manual feedback (e.g. automatic) opened via
     * {@link editFeedback} keeps requiring the explicit save button instead, see {@link updateFeedback}.
     */
    protected onFieldChanged(): void {
        if (this.isNewOrManual()) {
            this.commitFeedback();
        }
    }

    /**
     * Explicit save for a feedback that is not manual/new (e.g. automatic/static-analysis feedback opened via
     * {@link editFeedback}): manual/new feedback has no save button and commits on every change instead, see
     * {@link onFieldChanged}.
     */
    updateFeedback(): void {
        this.commitFeedback();
        this.viewOnly.set(true);
    }

    /**
     * Discards a feedback that was never actually saved (a freshly added, unsaved line): there is nothing to
     * persist, so just tell the parent to remove the widget.
     */
    cancelFeedback(): void {
        this.onCancelFeedback.emit(this.codeLine());
    }

    /**
     * Reverts an in-progress edit of a non-manual feedback (e.g. automatic/static-analysis, opened via
     * {@link editFeedback}) back to its last-saved state and returns to the collapsed view.
     */
    private revertFeedbackEdit(): void {
        const restored = this.oldFeedback();
        this.currentFeedback.set(restored);
        this.oldFeedback.set(deepClone(restored));
        this.viewOnly.set(true);
    }

    /**
     * Deletes feedback after confirmation and emits to parent component
     */
    deleteFeedback() {
        this.onDeleteFeedback.emit(this.currentFeedback());
    }

    /**
     * Handles the unified feedback's dismiss ("x") action, the only way left to remove a manual inline feedback: one
     * already bound via the {@link feedback} input is persisted, so it must actually be deleted; one that was never
     * bound (a freshly added, unsaved line) has nothing to delete and is just discarded. A non-manual feedback being
     * edited is never deleted this way - dismissing it only reverts the in-progress edit.
     */
    removeFeedback() {
        if (!this.isNewOrManual()) {
            this.revertFeedbackEdit();
        } else if (this.feedback()) {
            this.deleteFeedback();
        } else {
            this.cancelFeedback();
        }
    }

    /**
     * Opens a non-manual feedback (e.g. automatic/static-analysis) for editing and focuses its text area. Manual/new
     * feedback is always open already and never routes through here.
     * @param line Line of code which is emitted to the parent
     */
    editFeedback(line: number) {
        this.viewOnly.set(false);
        // Save the old feedback in case the user cancels later
        this.oldFeedback.set(deepClone(this.currentFeedback()));
        this.onEditFeedback.emit(line);
        setTimeout(() => this.unifiedFeedback()?.focusTextarea());
    }

    /**
     * Applies data from a dropped Structured Grading Instruction (SGI) to the feedback, then commits it the same
     * way a field edit would for manual/new feedback.
     * @param event Drop event with SGI data
     */
    updateFeedbackOnDrop(event: Event) {
        const feedback = this.currentFeedback();
        this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(feedback, event);
        this.onFieldChanged();
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
     * The listener is attached to the `<jhi-unified-feedback>` host element: the title/description/points inputs live
     * inside that component, and their keydown events bubble up to the host, so a single binding still covers them all.
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
