import { Component, ElementRef, OnDestroy, computed, inject, input, output, signal, viewChild } from '@angular/core';
import { Feedback, FeedbackType, buildFeedbackTextForReview } from 'app/assessment/shared/entities/feedback.model';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { GradingInstructionSelectionService } from 'app/exercise/structured-grading-criterion/grading-instruction-selection.service';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { Course } from 'app/course/shared/entities/course.model';
import { faBan, faPencilAlt, faSave } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TumAetUiButtonDirective } from '@tumaet/ui-angular';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { UnifiedFeedbackComponent } from 'app/shared/components/unified-feedback/unified-feedback.component';
import { deepClone } from 'app/foundation/util/deep-clone.util';

type EditableField = 'text' | 'detailText' | 'credits';

@Component({
    selector: 'jhi-code-editor-tutor-assessment-inline-feedback',
    templateUrl: './code-editor-tutor-assessment-inline-feedback.component.html',
    styleUrl: './code-editor-tutor-assessment-inline-feedback.component.css',
    imports: [TranslateDirective, FaIconComponent, UnifiedFeedbackComponent, TumAetUiButtonDirective],
    // Monaco anchors the widget on this host. Several cards may share a line, so the line is a data attribute,
    // not an HTML id that would be duplicated when two suggestions refer to the same location.
    host: {
        'data-testid': 'code-editor-inline-feedback',
        '[attr.data-code-line]': 'codeLine()',
        '[style.max-width.%]': '95',
    },
})
export class CodeEditorTutorAssessmentInlineFeedbackComponent implements OnDestroy {
    private readonly structuredGradingCriterionService = inject(StructuredGradingCriterionService);
    private readonly selectionService = inject(GradingInstructionSelectionService);
    public readonly elementRef = inject(ElementRef);

    protected readonly faSave = faSave;
    protected readonly faBan = faBan;
    protected readonly faPencilAlt = faPencilAlt;
    protected readonly Feedback = Feedback;

    /** The stored feedback, or undefined for a new card that has not been saved yet. */
    readonly feedback = input<Feedback>();
    readonly selectedFile = input.required<string>();
    readonly codeLine = input.required<number>();
    readonly readOnly = input.required<boolean>();
    readonly highlightDifferences = input(false);
    readonly course = input<Course>();
    readonly gradingCriteria = input<GradingCriterion[]>([]);

    readonly onUpdateFeedback = output<Feedback>();
    readonly onCancelFeedback = output<number>();
    readonly onDeleteFeedback = output<Feedback>();
    readonly onEditFeedback = output<number>();

    private readonly unifiedFeedback = viewChild(UnifiedFeedbackComponent);

    /**
     * Working copy of an open card. It is replaced, never mutated, so the computeds below track every edit, and it
     * is detached from {@link feedback} so the parent can rebind the stored feedback without discarding the edit.
     */
    private readonly draft = signal<Feedback | undefined>(undefined);
    /** Set by any edit, including raw input that has not reached the draft yet, such as a lone minus sign in the points field. */
    private readonly edited = signal(false);

    readonly viewOnly = computed(() => !!this.feedback() && !this.draft());
    readonly currentFeedback = computed(() => this.draft() ?? this.feedback() ?? new Feedback());

    protected readonly saveEnabled = computed(() => {
        const feedback = this.currentFeedback();
        return !this.readOnly() && Number.isFinite(feedback.credits) && Feedback.hasContent(feedback);
    });

    protected readonly displayTitle = computed(() => {
        const feedback = this.currentFeedback();
        const instructionId = feedback.gradingInstruction?.id;
        if (instructionId !== undefined) {
            const criterion = this.gradingCriteria().find((item) => item.structuredGradingInstructions?.some((instruction) => instruction.id === instructionId));
            if (criterion?.title) {
                return criterion.title;
            }
        }
        return Feedback.isFeedbackSuggestion(feedback) ? Feedback.stripSuggestionPrefix(feedback.text ?? '') || undefined : undefined;
    });

    protected readonly feedbackContent = computed(() => buildFeedbackTextForReview(this.currentFeedback(), false));

    /** A new card counts as unsaved; a stored one only while it is open and edited. The original stays in the assessment until Save. */
    hasUnsavedEdits(): boolean {
        return !this.feedback() || (!this.viewOnly() && this.edited());
    }

    protected setField<K extends EditableField>(field: K, value: Feedback[K]): void {
        const next = deepClone(this.currentFeedback());
        next[field] = value;
        this.commitDraft(next);
    }

    /** The shared card unlinks the instruction on the object it was given, so the draft is replaced to make the change visible. */
    protected onInstructionUnlinked(): void {
        this.commitDraft(deepClone(this.currentFeedback()));
    }

    protected markEdited(): void {
        this.edited.set(true);
    }

    private commitDraft(next: Feedback): void {
        this.draft.set(next);
        this.edited.set(true);
        // The stored link is already counted by the parent; reserve only an additional draft link.
        const instruction = next.gradingInstruction;
        this.selectionService.reserveInstruction(this, instruction?.id !== this.feedback()?.gradingInstruction?.id ? instruction : undefined);
    }

    private closeDraft(): void {
        this.draft.set(undefined);
        this.edited.set(false);
        this.selectionService.reserveInstruction(this);
    }

    updateFeedback(): void {
        if (!this.saveEnabled()) {
            return;
        }
        this.unifiedFeedback()?.onCreditsCommit();
        const feedback = deepClone(this.currentFeedback());
        feedback.type = FeedbackType.MANUAL;
        feedback.reference = `file:${this.selectedFile()}_line:${this.codeLine()}`;
        if (!feedback.text) {
            feedback.text = `File ${this.selectedFile()} at line ${this.codeLine() + 1}`;
        } else if (this.edited()) {
            feedback.text = Feedback.markAdaptedIfAcceptedSuggestion(feedback.text);
        }
        feedback.positive = (feedback.credits ?? 0) > 0;
        this.closeDraft();
        this.onUpdateFeedback.emit(feedback);
    }

    cancelFeedback(): void {
        if (this.readOnly()) {
            return;
        }
        this.closeDraft();
        if (!this.feedback()) {
            this.onCancelFeedback.emit(this.codeLine());
        }
    }

    removeFeedback(): void {
        if (this.readOnly()) {
            return;
        }
        const feedback = this.feedback();
        if (!feedback) {
            this.cancelFeedback();
            return;
        }
        this.closeDraft();
        this.onDeleteFeedback.emit(feedback);
    }

    editFeedback(): void {
        if (this.readOnly()) {
            return;
        }
        this.draft.set(deepClone(this.currentFeedback()));
        this.edited.set(false);
        this.onEditFeedback.emit(this.codeLine());
        setTimeout(() => this.unifiedFeedback()?.focusTextarea());
    }

    updateFeedbackOnDrop(event: Event): void {
        if (this.readOnly()) {
            return;
        }
        const next = deepClone(this.currentFeedback());
        if (!this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(next, event)) {
            return;
        }
        if (this.viewOnly()) {
            this.editFeedback();
        }
        this.linkInstruction(next);
    }

    applyArmedInstruction(): void {
        if (this.readOnly() || this.viewOnly()) {
            return;
        }
        const next = deepClone(this.currentFeedback());
        if (this.structuredGradingCriterionService.applyArmedInstructionToFeedback(next)) {
            this.linkInstruction(next);
        }
    }

    private linkInstruction(next: Feedback): void {
        if (next.text) {
            next.text = Feedback.markAdaptedIfAcceptedSuggestion(next.text);
        }
        this.commitDraft(next);
    }

    ngOnDestroy(): void {
        this.selectionService.reserveInstruction(this);
    }

    /**
     * Monaco forwards keys typed in a content widget to the editor, which would run its own commands, for example
     * Backspace deleting code in the read-only editor. Only the assessment shortcuts handled by the assessment
     * header (Ctrl+S, Ctrl+Enter, Ctrl+Shift+ArrowRight) may leave the card.
     */
    protected handleKeydown(event: KeyboardEvent): void {
        if (event.ctrlKey && (event.key.toLowerCase() === 's' || event.key === 'Enter' || (event.shiftKey && event.key === 'ArrowRight'))) {
            return;
        }
        event.stopPropagation();
    }
}
