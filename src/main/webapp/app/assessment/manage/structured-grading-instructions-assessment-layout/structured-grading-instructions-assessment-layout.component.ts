import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { Component, OnDestroy, computed, inject, input, viewChildren } from '@angular/core';
import { faCrosshairs, faInfoCircle, faMinus, faPlus } from '@fortawesome/free-solid-svg-icons';
import { ExpandableSectionComponent } from 'app/assessment/manage/assessment-instructions/expandable-section/expandable-section.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { GradingInstructionSelectionService } from 'app/exercise/structured-grading-criterion/grading-instruction-selection.service';
import { TumAetUiButtonComponent, TumAetUiCheckboxComponent, TumAetUiMessageComponent, TumAetUiTagComponent, TumAetUiTooltipDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';

/** A criterion prepared for display in instructor order, together with its live "applied" counter. */
export interface SortedGradingCriterion {
    title: string;
    instructions: GradingInstruction[];
}

@Component({
    selector: 'jhi-structured-grading-instructions-assessment-layout',
    templateUrl: './structured-grading-instructions-assessment-layout.component.html',
    styleUrls: ['./structured-grading-instructions-assessment-layout.component.scss'],
    imports: [
        FaIconComponent,
        TranslateDirective,
        ExpandableSectionComponent,
        HelpIconComponent,
        MarkdownDirective,
        TumAetUiCheckboxComponent,
        TumAetUiTagComponent,
        TumAetUiButtonComponent,
        TumAetUiMessageComponent,
        TumAetUiTooltipDirective,
        ArtemisTranslatePipe,
    ],
})
export class StructuredGradingInstructionsAssessmentLayoutComponent implements OnDestroy {
    protected readonly selectionService = inject(GradingInstructionSelectionService);
    private readonly deleteDialogService = inject(DeleteDialogService);

    public readonly criteria = input.required<GradingCriterion[]>();
    readonly readonly = input<boolean>();
    readonly allowArming = input(true);
    readonly allowDrop = computed(() => !this.readonly());
    // Icons
    faInfoCircle = faInfoCircle;
    faCrosshairs = faCrosshairs;
    faMinus = faMinus;
    faPlus = faPlus;

    readonly expandableSections = viewChildren(ExpandableSectionComponent);

    /**
     * Preserve the instructor's rubric order in the assessment editor.
     */
    readonly sortedCriteria = computed<SortedGradingCriterion[]>(() =>
        (this.criteria() ?? []).map((criterion) => ({
            title: criterion.title,
            instructions: criterion.structuredGradingInstructions ?? [],
        })),
    );

    readonly selectable = computed(() => !this.readonly() && this.selectionService.isSelectable());

    readonly appliedCountPerCriterion = computed(() => {
        const applied = this.selectionService.appliedInstructionIds();
        return this.sortedCriteria().map((criterion) => criterion.instructions.filter((instruction) => instruction.id !== undefined && applied.has(instruction.id)).length);
    });

    collapseAll() {
        this.expandableSections().forEach((section) => {
            if (!section.isCollapsed()) {
                section.toggleCollapsed();
            }
        });
    }

    expandAll() {
        this.expandableSections().forEach((section) => {
            if (section.isCollapsed()) {
                section.toggleCollapsed();
            }
        });
    }

    isApplied(instruction: GradingInstruction): boolean {
        return this.selectionService.isApplied(instruction);
    }

    /**
     * Whether the instruction is applied to a referenced element (a line of code, a diagram element, a text block)
     * only. Its feedback belongs to that element, so it can be removed there but not from the feedback list.
     */
    isLockedByReferencedFeedback(instruction: GradingInstruction): boolean {
        return this.isApplied(instruction) && !this.selectionService.isRemovable(instruction);
    }

    /**
     * Drag stays enabled until a finite {@link GradingInstruction.usageCount} is exhausted. Zero or an unset limit
     * means unlimited, so the instruction can be dropped onto further targets even after it was checked.
     */
    isDraggable(instruction: GradingInstruction): boolean {
        if (!this.allowDrop()) {
            return false;
        }
        const usageLimit = instruction.usageCount ?? 0;
        if (usageLimit <= 0) {
            return true;
        }
        return this.selectionService.applicationCount(instruction) < usageLimit;
    }

    /**
     * Applying an instruction via its checkbox is equivalent to dropping it onto the feedback list. Un-applying it
     * deletes every feedback card that instruction produced.
     */
    toggleApplied(event: Event, instruction: GradingInstruction): void {
        event.preventDefault();
        if (this.isLockedByReferencedFeedback(instruction)) {
            return;
        }
        if (!this.isApplied(instruction)) {
            this.selectionService.setApplied(instruction, true);
            return;
        }
        this.deleteDialogService.openDeleteDialog({
            deleteQuestion: 'artemisApp.feedback.delete.allInstances',
            translateValues: { text: '' },
            actionType: ActionType.Delete,
            buttonType: ButtonType.ERROR,
            requireConfirmationOnlyForAdditionalChecks: false,
            delete: () => this.selectionService.setApplied(instruction, false),
        });
    }

    /** One more application, while a finite usage limit (if any) still has room. */
    incrementApplication(event: Event, instruction: GradingInstruction): void {
        event.preventDefault();
        event.stopPropagation();
        if (!this.canIncrementApplication(instruction)) {
            return;
        }
        this.selectionService.addApplication(instruction);
    }

    /** One fewer application owned by the feedback list. Referenced-only applications stay put. */
    decrementApplication(event: Event, instruction: GradingInstruction): void {
        event.preventDefault();
        event.stopPropagation();
        if (!this.canDecrementApplication(instruction)) {
            return;
        }
        this.selectionService.removeOneApplication(instruction);
    }

    canIncrementApplication(instruction: GradingInstruction): boolean {
        return this.isDraggable(instruction);
    }

    canDecrementApplication(instruction: GradingInstruction): boolean {
        return this.selectionService.isRemovable(instruction);
    }

    /**
     * {@link GradingInstruction.usageCount} of 1 is a single application. Zero/unset means unlimited, and any higher
     * value is a finite multi-use limit — both need the stepper once applied.
     */
    isMultiUse(instruction: GradingInstruction): boolean {
        return (instruction.usageCount ?? 0) !== 1;
    }

    /** Multi-use instructions always expose the counter; single-use stays checkbox-only. */
    showUsageStepper(instruction: GradingInstruction): boolean {
        return this.isMultiUse(instruction);
    }

    setScore(nr: number) {
        return nr > 0 ? `+${nr}` : `${nr}`;
    }
    /**
     * Connects the SGI with the Feedback of a Submission Element in assessment detail
     * @param {Event} event - The drag event
     * @param {Object} instruction - The SGI element that should be connected with the feedback on drop
     * the corresponding drop method is in AssessmentDetailComponent
     */
    drag(event: DragEvent, instruction: GradingInstruction) {
        if (!this.isDraggable(instruction)) {
            event.preventDefault();
            return;
        }
        // The mimetype has to be text/plain to enable dragging into an external application, e.g, Apollon
        event.dataTransfer?.setData('text/plain', JSON.stringify(instruction));
    }
    /**
     * disables drag if on readOnly mode
     */
    /**
     * Keyboard stand-in for drag-and-drop when there is no checkbox host: Enter/Space on the card toggles the instruction.
     * Selectable cards use {@link armInstruction} on their dedicated button instead (nested controls forbid role=button).
     */
    onInstructionKeydown(event: KeyboardEvent, instruction: GradingInstruction): void {
        if (!this.allowArming() || this.selectable() || !this.isDraggable(instruction)) {
            return;
        }
        if (event.key !== 'Enter' && event.key !== ' ') {
            return;
        }
        event.preventDefault();
        this.selectionService.toggleArmedInstruction(instruction);
    }

    /**
     * Selectable-branch stand-in for drag-and-drop: toggles the instruction for the next referenced feedback target.
     * Checkboxes stay the path for the unreferenced feedback list; existing target consumers still apply the armed instruction.
     */
    armInstruction(event: Event, instruction: GradingInstruction): void {
        event.preventDefault();
        event.stopPropagation();
        if (!this.allowArming() || !this.isDraggable(instruction)) {
            return;
        }
        this.selectionService.toggleArmedInstruction(instruction);
    }

    ngOnDestroy(): void {
        this.selectionService.clearArmedInstruction();
    }
}
