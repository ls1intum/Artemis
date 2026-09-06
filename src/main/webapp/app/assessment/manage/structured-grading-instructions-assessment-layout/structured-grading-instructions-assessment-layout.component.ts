import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { Component, OnDestroy, OnInit, computed, inject, input, signal, viewChildren } from '@angular/core';
import { faInfoCircle, faMinus, faPlus } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExpandableSectionComponent } from 'app/assessment/manage/assessment-instructions/expandable-section/expandable-section.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { GradingInstructionSelectionService } from 'app/exercise/structured-grading-criterion/grading-instruction-selection.service';
import { TumUiButtonComponent, TumUiCheckboxComponent, TumUiMessageComponent, TumUiTagComponent, TumUiTagSeverity, TumUiTooltipDirective } from '@tumaet/ui-angular';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { pointsLabel, pointsSeverity } from 'app/exercise/structured-grading-criterion/grading-points-display.util';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';

/** One instruction prepared for display so the template reads properties instead of calling formatting/state methods. */
export interface SortedGradingInstruction {
    instruction: GradingInstruction;
    useCount: number;
    usageLimit: number;
    isDraggable: boolean;
    showUsageStepper: boolean;
    canDecrementApplication: boolean;
    canIncrementApplication: boolean;
    isLockedByReferencedFeedback: boolean;
    isApplied: boolean;
    pointsSeverity: TumUiTagSeverity;
    pointsLabel: string;
    scaleElementId: string | undefined;
    descriptionElementId: string;
    accessibleNameIds: string;
}

/** A criterion prepared for display: alphabetically sorted instructions plus its live "applied" counter. */
export interface SortedGradingCriterion {
    title: string;
    instructions: SortedGradingInstruction[];
}

@Component({
    selector: 'jhi-structured-grading-instructions-assessment-layout',
    templateUrl: './structured-grading-instructions-assessment-layout.component.html',
    styleUrls: ['./structured-grading-instructions-assessment-layout.component.scss'],
    imports: [
        TranslateDirective,
        ExpandableSectionComponent,
        HelpIconComponent,
        MarkdownDirective,
        FaIconComponent,
        TumUiCheckboxComponent,
        TumUiTagComponent,
        TumUiButtonComponent,
        TumUiMessageComponent,
        TumUiTooltipDirective,
        ArtemisTranslatePipe,
    ],
})
export class StructuredGradingInstructionsAssessmentLayoutComponent implements OnInit, OnDestroy {
    private readonly selectionService = inject(GradingInstructionSelectionService);
    private readonly deleteDialogService = inject(DeleteDialogService);

    public readonly criteria = input.required<GradingCriterion[]>();
    readonly readonly = input<boolean>();
    readonly allowDrop = signal<boolean>(undefined!);
    // Icons
    faInfoCircle = faInfoCircle;
    faMinus = faMinus;
    faPlus = faPlus;

    readonly expandableSections = viewChildren(ExpandableSectionComponent);

    /**
     * Criteria and their instructions in alphabetical order, so a tutor can find an instruction by its wording
     * instead of by the (arbitrary) order in which the instructor happened to create them.
     */
    readonly sortedCriteria = computed<SortedGradingCriterion[]>(() =>
        [...(this.criteria() ?? [])]
            .sort((a, b) => (a.title ?? '').localeCompare(b.title ?? ''))
            .map((criterion, criterionIndex) => ({
                title: criterion.title,
                instructions: [...(criterion.structuredGradingInstructions ?? [])]
                    .sort((a, b) => (a.instructionDescription ?? '').localeCompare(b.instructionDescription ?? ''))
                    .map((instruction, instructionIndex) => {
                        const prefix = `criterion-${criterionIndex}-instruction-${instructionIndex}`;
                        const scaleElementId = instruction.gradingScale ? `${prefix}-scale` : undefined;
                        const descriptionElementId = `${prefix}-desc`;
                        return {
                            instruction,
                            useCount: this.selectionService.applicationCount(instruction),
                            usageLimit: instruction.usageCount ?? 0,
                            isDraggable: this.isDraggable(instruction),
                            showUsageStepper: this.showUsageStepper(instruction),
                            canDecrementApplication: this.canDecrementApplication(instruction),
                            canIncrementApplication: this.canIncrementApplication(instruction),
                            isLockedByReferencedFeedback: this.isLockedByReferencedFeedback(instruction),
                            isApplied: this.isApplied(instruction),
                            pointsSeverity: pointsSeverity(instruction.credits),
                            pointsLabel: pointsLabel(instruction.credits),
                            scaleElementId,
                            descriptionElementId,
                            accessibleNameIds: [scaleElementId, descriptionElementId].filter((id): id is string => id !== undefined).join(' '),
                        };
                    }),
            })),
    );

    readonly selectable = computed(() => !this.readonly() && this.selectionService.isSelectable());

    readonly appliedCountPerCriterion = computed(() => {
        const applied = this.selectionService.appliedInstructionIds();
        return this.sortedCriteria().map((criterion) => criterion.instructions.filter(({ instruction }) => instruction.id !== undefined && applied.has(instruction.id)).length);
    });

    /**
     * OnInit set the allowDrop property to allow drop of SGI if not in readOnly mode
     */
    ngOnInit(): void {
        this.allowDrop.set(!this.readonly());
    }

    ngOnDestroy(): void {
        this.selectionService.clearArmedInstruction();
    }

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
            translateValues: {},
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

    /** Tag severity of an instruction's point pill (green awarded / red deducted / neutral zero). */
    pointsSeverity(credits: number): TumUiTagSeverity {
        return pointsSeverity(credits);
    }

    /** Signed, compact label of an instruction's point pill, e.g. `+10`, `-5`. */
    pointsLabel(credits: number): string {
        return pointsLabel(credits);
    }

    /** Number of feedback entries currently linked to this instruction in the open assessment. */
    instructionUseCount(instruction: GradingInstruction): number {
        return this.selectionService.applicationCount(instruction);
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
     * Keyboard stand-in for drag-and-drop when there is no checkbox host: Enter/Space on the card arms the instruction.
     * Selectable cards use {@link armInstruction} on their dedicated button instead (nested controls forbid role=button).
     */
    onInstructionKeydown(event: KeyboardEvent, instruction: GradingInstruction): void {
        if (this.selectable() || !this.isDraggable(instruction)) {
            return;
        }
        if (event.key !== 'Enter' && event.key !== ' ') {
            return;
        }
        event.preventDefault();
        this.selectionService.armInstruction(instruction);
    }

    /**
     * Selectable-branch stand-in for drag-and-drop: arms the instruction for the next referenced feedback target.
     * Checkboxes stay the path for the unreferenced feedback list; existing target consumers still apply the armed instruction.
     */
    armInstruction(event: Event, instruction: GradingInstruction): void {
        event.preventDefault();
        event.stopPropagation();
        if (!this.isDraggable(instruction)) {
            return;
        }
        this.selectionService.armInstruction(instruction);
    }

    /**
     * disables drag if on readOnly mode
     */
    disableDrag() {
        return this.allowDrop();
    }
}
