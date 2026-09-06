import { Component, ElementRef, computed, inject, input, linkedSignal, output, signal, viewChild } from '@angular/core';
import { Feedback, FeedbackType, buildFeedbackTextForReview } from 'app/assessment/shared/entities/feedback.model';
import { FeedbackSuggestionBadgeComponent } from 'app/exercise/feedback/feedback-suggestion-badge/feedback-suggestion-badge.component';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { GradingInstructionSelectionService } from 'app/exercise/structured-grading-criterion/grading-instruction-selection.service';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { roundValueSpecifiedByCourseSettings } from 'app/foundation/util/utils';
import { Course } from 'app/course/shared/entities/course.model';
import { faBan, faExclamationTriangle, faMinus, faPencilAlt, faPlus, faSave, faTimes, faTrashAlt } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { GradingInstructionLinkIconComponent } from 'app/shared-ui/grading-instruction-link-icon/grading-instruction-link-icon.component';
import { FormsModule } from '@angular/forms';
import { ConfirmIconComponent } from 'app/shared-ui/confirm-icon/confirm-icon.component';
import { AssessmentCorrectionRoundBadgeComponent } from 'app/assessment/manage/unreferenced-feedback-detail/assessment-correction-round-badge/assessment-correction-round-badge.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import {
    TumUiButtonDirective,
    TumUiCardComponent,
    TumUiInputDirective,
    TumUiInputGroupAddonComponent,
    TumUiInputGroupComponent,
    TumUiTagComponent,
    TumUiTagSeverity,
    TumUiTooltipDirective,
} from '@tumaet/ui-angular';
import { CREDITS_STEP, normalizedCredits, pointsSeverity, steppedCredits } from 'app/exercise/structured-grading-criterion/grading-points-display.util';
import { FeedbackTone } from 'app/assessment/manage/unreferenced-feedback-detail/unreferenced-feedback-detail.component';

@Component({
    selector: 'jhi-code-editor-tutor-assessment-inline-feedback',
    templateUrl: './code-editor-tutor-assessment-inline-feedback.component.html',
    styleUrls: ['./code-editor-tutor-assessment-inline-feedback.component.scss'],
    imports: [
        FeedbackSuggestionBadgeComponent,
        TranslateDirective,
        FaIconComponent,
        GradingInstructionLinkIconComponent,
        FormsModule,
        ConfirmIconComponent,
        AssessmentCorrectionRoundBadgeComponent,
        ArtemisTranslatePipe,
        TumUiCardComponent,
        TumUiButtonDirective,
        TumUiTagComponent,
        TumUiInputDirective,
        TumUiInputGroupComponent,
        TumUiInputGroupAddonComponent,
        TumUiTooltipDirective,
    ],
})
export class CodeEditorTutorAssessmentInlineFeedbackComponent {
    protected readonly faSave = faSave;
    protected readonly faBan = faBan;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly faTrashAlt = faTrashAlt;
    protected readonly faTimes = faTimes;
    protected readonly faExclamationTriangle = faExclamationTriangle;
    protected readonly faMinus = faMinus;
    protected readonly faPlus = faPlus;
    protected readonly Feedback = Feedback;
    protected readonly MANUAL = FeedbackType.MANUAL;
    protected readonly CREDITS_STEP = CREDITS_STEP;

    // Expose the function to the template. The feedback of this widget is edited in place (see currentFeedback), so
    // its presentation is derived per change detection run instead of through computed signals.
    protected readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;

    private structuredGradingCriterionService = inject(StructuredGradingCriterionService);
    private readonly selectionService = inject(GradingInstructionSelectionService);
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

    /**
     * Bumped when {@link currentFeedback} content changes in place (textarea / instruction link) so {@link saveEnabled}
     * re-evaluates — a plain method read of the same object identity would stay stale under signal CD.
     */
    private readonly contentRevision = signal(0);

    /** Reactive stand-in for {@link canSave} in the template. */
    readonly saveEnabled = computed(() => {
        this.contentRevision();
        this.currentFeedback();
        return this.canSave();
    });

    readonly selectedFile = input.required<string>();

    readonly codeLine = input.required<number>();

    readonly readOnly = input.required<boolean>();
    readonly highlightDifferences = input<boolean>();
    readonly course = input<Course>();
    readonly gradingCriteria = input<GradingCriterion[]>([]);
    readonly textareaRef = viewChild<ElementRef>('detailText');

    readonly onUpdateFeedback = output<Feedback>();
    readonly onCancelFeedback = output<number>();
    readonly onDeleteFeedback = output<Feedback>();
    readonly onEditFeedback = output<number>();
    /**
     * Unsaved new inline cards are not in the parent's referenced list until save. Emit the draft (or `undefined`
     * when unlinked) so instruction usage counts can include pending links.
     */
    readonly onPendingFeedbackChange = output<Feedback | undefined>();

    /** Shows the apply-armed-instruction control while an instruction is armed and the card is open for editing. */
    protected readonly isKeyboardDropTarget = computed(() => !this.readOnly() && !this.viewOnly() && this.selectionService.hasArmedInstruction());

    /** Keeps an open edit session stable when Monaco rebinds a deep-cloned feedback after an update. */
    private readonly editSessionActive = signal(false);

    /**
     * Whether the feedback is rendered in read-only mode. Mirrors the original setter behavior: it is `true` whenever a
     * feedback was bound via the input and resets accordingly when the input changes, unless an edit session is active.
     */
    readonly viewOnly = linkedSignal<Feedback | undefined, boolean>({
        source: () => this.feedback(),
        computation: (feedback, previous) => (this.editSessionActive() && previous ? previous.value : !!feedback),
    });

    /**
     * Edit-start snapshot for Cancel. Independent of {@link feedback}: emitting {@link onUpdateFeedback} mid-edit
     * (e.g. instruction drop after a detached point change) updates the parent list and would otherwise reset a
     * linked snapshot to the dirty draft, so Cancel could no longer restore the original.
     */
    readonly oldFeedback = signal<Feedback>(new Feedback());

    /**
     * Criterion title for instruction-linked feedback, else suggestion title. Method (not computed): drop/unlink
     * mutate {@link currentFeedback}.gradingInstruction in place without a new signal identity.
     */
    protected displayTitle(): string | undefined {
        const feedback = this.currentFeedback();
        const criterionTitle = this.structuredGradingCriterionService.findCriterionTitle(this.gradingCriteria(), feedback.gradingInstruction?.id);
        if (criterionTitle) {
            return criterionTitle;
        }
        if (feedback.text && Feedback.getFeedbackSuggestionPrefix(feedback.text)) {
            return Feedback.getDisplayTitle(feedback);
        }
        return undefined;
    }
    /**
     * Updates the current feedback and sets props and emits the feedback to parent component
     */
    updateFeedback() {
        if (!this.canSave()) {
            return;
        }
        const feedback = deepClone(this.currentFeedback());
        feedback.credits = normalizedCredits(feedback.credits);
        feedback.type = this.MANUAL;
        feedback.reference = `file:${this.selectedFile()}_line:${this.codeLine()}`;
        if (Feedback.isFeedbackSuggestion(feedback)) {
            Feedback.updateFeedbackTypeOnChange(feedback);
        } else {
            feedback.text = `File ${this.selectedFile()} at line ${this.codeLine() + 1}`;
        }
        this.editSessionActive.set(false);
        this.viewOnly.set(true);
        if (feedback.credits && feedback.credits > 0) {
            feedback.positive = true;
        }
        this.currentFeedback.set(feedback);
        // Align cancel snapshot with the saved card so a later edit/cancel pair is coherent if editFeedback is skipped.
        this.oldFeedback.set(deepClone(feedback));
        this.emitUpdate(feedback);
    }

    /** Emits a feedback update to the parent. */
    private emitUpdate(feedback: Feedback): void {
        this.onUpdateFeedback.emit(feedback);
    }

    /**
     * Save needs student-facing text (own comment and/or linked instruction feedback). Points may be zero.
     * Live read for {@link updateFeedback}; the template uses {@link saveEnabled} instead.
     */
    private canSave(): boolean {
        return Feedback.hasContent(this.currentFeedback());
    }

    /**
     * Writes the comment onto a fresh {@link currentFeedback} copy so edits stay aligned with {@link stepCredits}
     * and {@link updateFeedback} after the working copy is replaced.
     */
    protected onDetailTextChange(detailText: string): void {
        const feedback = deepClone(this.currentFeedback());
        feedback.detailText = detailText;
        this.currentFeedback.set(feedback);
    }

    /**
     * When an inline feedback already exists, we set it back and display it the viewOnly mode.
     * Otherwise, the component is not displayed anymore in the parent component
     */
    cancelFeedback() {
        const restored = this.oldFeedback();
        this.currentFeedback.set(restored);
        this.oldFeedback.set(deepClone(restored));
        this.editSessionActive.set(false);
        this.viewOnly.set(restored.type === this.MANUAL);
        if (this.feedback()) {
            // Existing card: push restored state so in-place link/unlink during edit reverts in usage counts.
            this.emitUpdate(restored);
        } else {
            this.onPendingFeedbackChange.emit(undefined);
            this.onCancelFeedback.emit(this.codeLine());
        }
    }

    /** Whether the feedback awards, deducts or changes nothing — the widget's left accent stripe follows it. */
    protected tone(feedback: Feedback): FeedbackTone {
        if (this.isExcludedFromScore(feedback)) {
            return 'neutral';
        }
        const credits = feedback.credits ?? 0;
        if (credits > 0) {
            return 'positive';
        }
        return credits < 0 ? 'negative' : 'neutral';
    }

    /** Severity of the point pill (green awarded / red deducted / neutral). */
    protected pointsSeverity(feedback: Feedback): TumUiTagSeverity {
        return this.isExcludedFromScore(feedback) ? 'secondary' : pointsSeverity(feedback.credits);
    }

    /** Subsequent feedback of an earlier correction round is shown for context only and adds nothing to the score. */
    private isExcludedFromScore(feedback: Feedback): boolean {
        return this.readOnly() && !!feedback.isSubsequent;
    }

    /**
     * Increments or decrements the points by one half-point step, mirroring what typing into the field does.
     * @param delta the signed step to apply
     */
    protected stepCredits(delta: number): void {
        const feedback = deepClone(this.currentFeedback());
        // Points of a feedback linked to a grading instruction are owned by that instruction.
        if (feedback.gradingInstruction) {
            return;
        }
        feedback.credits = steppedCredits(feedback.credits, delta);
        this.currentFeedback.set(feedback);
    }

    protected updateCredits(credits: number | null | undefined): void {
        const feedback = deepClone(this.currentFeedback());
        feedback.credits = normalizedCredits(credits);
        this.currentFeedback.set(feedback);
    }

    /**
     * Deletes feedback after confirmation and emits to parent component.
     * Existing cards: Monaco removes via {@link Feedback.areIdentical} against the list item. The textarea can mutate
     * that object in place before a point edit detaches {@link currentFeedback}, so emit the bound {@link feedback}
     * input (the list item) — not {@link oldFeedback}, whose detailText may already be stale.
     */
    deleteFeedback() {
        this.onDeleteFeedback.emit(this.feedback() ?? this.currentFeedback());
    }

    /**
     * Checks if component is in view mode and focuses feedback text area
     * @param line Line of code which is emitted to the parent
     */
    editFeedback(line: number) {
        this.editSessionActive.set(true);
        this.viewOnly.set(false);
        // Save the old feedback in case the user cancels later
        this.oldFeedback.set(deepClone(this.currentFeedback()));
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
        this.contentRevision.update((revision) => revision + 1);
        this.notifyInstructionLinkChange(feedback);
    }

    /** Applies a previously armed instruction to this feedback. */
    applyArmedInstruction(): void {
        if (!this.isKeyboardDropTarget()) {
            return;
        }
        const feedback = this.currentFeedback();
        if (!this.structuredGradingCriterionService.applyArmedInstructionToFeedback(feedback)) {
            return;
        }
        feedback.reference = `file:${this.selectedFile()}_line:${this.codeLine()}`;
        feedback.text = `File ${this.selectedFile()} at line ${this.codeLine() + 1}`;
        this.contentRevision.update((revision) => revision + 1);
        this.notifyInstructionLinkChange(feedback);
    }

    /** Unlink via {@link GradingInstructionLinkIconComponent} — refresh parent usage counts. */
    protected onInstructionLinkRemoved(): void {
        this.contentRevision.update((revision) => revision + 1);
        this.notifyInstructionLinkChange(this.currentFeedback());
    }

    /**
     * Existing cards live in the parent's referenced list: emit {@link onUpdateFeedback} so zoneless CD refreshes
     * counts. New drafts are only local until save: emit {@link onPendingFeedbackChange} instead.
     */
    private notifyInstructionLinkChange(feedback: Feedback): void {
        if (this.feedback()) {
            this.emitUpdate(feedback);
            return;
        }
        this.onPendingFeedbackChange.emit(feedback.gradingInstruction ? feedback : undefined);
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
