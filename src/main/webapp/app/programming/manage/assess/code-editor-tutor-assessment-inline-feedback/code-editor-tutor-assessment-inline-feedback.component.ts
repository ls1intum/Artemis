import { Component, ElementRef, OnDestroy, computed, inject, input, linkedSignal, output, signal, viewChild } from '@angular/core';
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

@Component({
    selector: 'jhi-code-editor-tutor-assessment-inline-feedback',
    templateUrl: './code-editor-tutor-assessment-inline-feedback.component.html',
    styleUrl: './code-editor-tutor-assessment-inline-feedback.component.scss',
    imports: [TranslateDirective, FaIconComponent, UnifiedFeedbackComponent, TumAetUiButtonDirective],
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
    readonly feedback = input<Feedback>();
    readonly selectedFile = input.required<string>();
    readonly codeLine = input.required<number>();
    readonly readOnly = input.required<boolean>();
    readonly highlightDifferences = input(false);
    readonly course = input<Course>();
    readonly gradingCriteria = input<GradingCriterion[]>([]);
    private readonly unifiedFeedback = viewChild(UnifiedFeedbackComponent);
    private readonly editing = signal(false);
    protected readonly inputEdited = signal(false);
    private readonly revision = signal(0);

    // Parent updates to other cards must not replace this card's working copy.
    readonly currentFeedback = linkedSignal<Feedback | undefined, Feedback>({
        source: () => this.feedback(),
        computation: (feedback, previous) => (this.editing() && previous ? previous.value : deepClone(feedback ?? new Feedback())),
    });
    readonly viewOnly = linkedSignal<Feedback | undefined, boolean>({
        source: () => this.feedback(),
        computation: (feedback, previous) => (this.editing() && previous ? previous.value : !!feedback),
    });
    readonly onUpdateFeedback = output<Feedback>();
    readonly onCancelFeedback = output<number>();
    readonly onDeleteFeedback = output<Feedback>();
    readonly onEditFeedback = output<number>();

    protected readonly saveEnabled = computed(() => {
        this.revision();
        const feedback = this.currentFeedback();
        return !this.readOnly() && Number.isFinite(feedback.credits) && Feedback.hasContent(feedback);
    });
    protected readonly isKeyboardDropTarget = computed(() => !this.readOnly() && !this.viewOnly() && this.selectionService.hasArmedInstruction());

    protected displayTitle(): string | undefined {
        const feedback = this.currentFeedback();
        const instructionId = feedback.gradingInstruction?.id;
        const criterion =
            instructionId === undefined
                ? undefined
                : this.gradingCriteria().find((item) => item.structuredGradingInstructions?.some((instruction) => instruction.id === instructionId));
        return criterion?.title || (Feedback.isFeedbackSuggestion(feedback) ? Feedback.getDisplayTitle(feedback) : undefined);
    }

    /** The original stays in the assessment until Save, including its instruction usage. */
    hasUnsavedEdits(): boolean {
        if (!this.feedback()) {
            return true;
        }
        const current = this.currentFeedback();
        const original = this.feedback()!;
        return (
            !this.viewOnly() &&
            (this.inputEdited() ||
                current.text !== original.text ||
                current.detailText !== original.detailText ||
                current.credits !== original.credits ||
                current.gradingInstruction?.id !== original.gradingInstruction?.id)
        );
    }

    protected onFieldChanged(): void {
        this.revision.update((value) => value + 1);
        const instruction = this.currentFeedback().gradingInstruction;
        // The original link is already counted by the parent; reserve only an additional draft link.
        this.selectionService.reserveInstruction(this, instruction?.id !== this.feedback()?.gradingInstruction?.id ? instruction : undefined);
    }

    updateFeedback(): void {
        if (!this.saveEnabled()) {
            return;
        }
        this.unifiedFeedback()?.onCreditsCommit();
        const feedback = deepClone(this.currentFeedback());
        feedback.type = FeedbackType.MANUAL;
        feedback.reference = `file:${this.selectedFile()}_line:${this.codeLine()}`;
        feedback.text = feedback.text ? Feedback.markAdaptedIfAcceptedSuggestion(feedback.text) : `File ${this.selectedFile()} at line ${this.codeLine() + 1}`;
        feedback.positive = (feedback.credits ?? 0) > 0;
        this.editing.set(false);
        this.inputEdited.set(false);
        this.currentFeedback.set(feedback);
        this.viewOnly.set(true);
        this.selectionService.reserveInstruction(this);
        this.onUpdateFeedback.emit(feedback);
    }

    cancelFeedback(): void {
        if (this.readOnly()) {
            return;
        }
        this.editing.set(false);
        this.inputEdited.set(false);
        this.selectionService.reserveInstruction(this);
        this.currentFeedback.set(deepClone(this.feedback() ?? new Feedback()));
        this.viewOnly.set(!!this.feedback());
        if (!this.feedback()) {
            this.onCancelFeedback.emit(this.codeLine());
        }
    }

    deleteFeedback(): void {
        if (!this.readOnly()) {
            this.selectionService.reserveInstruction(this);
            this.onDeleteFeedback.emit(this.feedback() ?? this.currentFeedback());
        }
    }

    removeFeedback(): void {
        if (this.feedback()) {
            this.deleteFeedback();
        } else {
            this.cancelFeedback();
        }
    }

    editFeedback(line: number): void {
        if (this.readOnly()) {
            return;
        }
        this.currentFeedback.set(deepClone(this.feedback() ?? new Feedback()));
        this.editing.set(true);
        this.inputEdited.set(false);
        this.viewOnly.set(false);
        this.onEditFeedback.emit(line);
        setTimeout(() => this.unifiedFeedback()?.focusTextarea());
    }

    updateFeedbackOnDrop(event: Event): void {
        if (!this.readOnly()) {
            if (this.viewOnly()) {
                this.editFeedback(this.codeLine());
            }
            this.structuredGradingCriterionService.updateFeedbackWithStructuredGradingInstructionEvent(this.currentFeedback(), event);
            this.onInstructionLinked();
        }
    }

    applyArmedInstruction(): void {
        if (this.isKeyboardDropTarget() && this.structuredGradingCriterionService.applyArmedInstructionToFeedback(this.currentFeedback())) {
            this.onInstructionLinked();
        }
    }

    private onInstructionLinked(): void {
        const feedback = this.currentFeedback();
        if (feedback.text) {
            feedback.text = Feedback.markAdaptedIfAcceptedSuggestion(feedback.text);
        }
        this.onFieldChanged();
    }

    ngOnDestroy(): void {
        this.selectionService.reserveInstruction(this);
    }

    public buildFeedbackTextForCodeEditor(feedback: Feedback): string {
        return buildFeedbackTextForReview(feedback, false);
    }

    protected handleKeydown(event: KeyboardEvent): void {
        if (event.ctrlKey && (event.key.toLowerCase() === 's' || event.key === 'Enter' || (event.shiftKey && event.key === 'ArrowRight'))) {
            return;
        }
        event.stopPropagation();
    }
}
