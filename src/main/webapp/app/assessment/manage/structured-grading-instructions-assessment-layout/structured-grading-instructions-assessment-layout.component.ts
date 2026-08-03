import { GradingInstruction } from 'app/exercise/structured-grading-criterion/grading-instruction.model';
import { GradingCriterion } from 'app/exercise/structured-grading-criterion/grading-criterion.model';
import { Component, OnInit, computed, inject, input, signal, viewChildren } from '@angular/core';
import { faCompress, faExpand, faInfoCircle } from '@fortawesome/free-solid-svg-icons';
import { ExpandableSectionComponent } from 'app/assessment/manage/assessment-instructions/expandable-section/expandable-section.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { MarkdownDirective } from 'app/foundation/directives/markdown.directive';
import { GradingInstructionSelectionService } from 'app/exercise/structured-grading-criterion/grading-instruction-selection.service';
import { TumUiCheckboxComponent } from 'app/shared-ui/tum-ui/checkbox/tum-ui-checkbox.component';
import { TumUiTagComponent } from 'app/shared-ui/tum-ui/tag/tum-ui-tag.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { ButtonType } from 'app/shared-ui/components/buttons/button/button.component';

/** A criterion prepared for display: alphabetically sorted instructions plus its live "applied" counter. */
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
        NgbTooltip,
        HelpIconComponent,
        MarkdownDirective,
        TumUiCheckboxComponent,
        TumUiTagComponent,
        ArtemisTranslatePipe,
    ],
})
export class StructuredGradingInstructionsAssessmentLayoutComponent implements OnInit {
    private readonly selectionService = inject(GradingInstructionSelectionService);
    private readonly deleteDialogService = inject(DeleteDialogService);

    public readonly criteria = input.required<GradingCriterion[]>();
    readonly readonly = input<boolean>();
    readonly allowDrop = signal<boolean>(undefined!);
    // Icons
    faInfoCircle = faInfoCircle;
    faExpand = faExpand;
    faCompress = faCompress;

    readonly expandableSections = viewChildren(ExpandableSectionComponent);

    /**
     * Criteria and their instructions in alphabetical order, so a tutor can find an instruction by its wording
     * instead of by the (arbitrary) order in which the instructor happened to create them.
     */
    readonly sortedCriteria = computed<SortedGradingCriterion[]>(() =>
        [...(this.criteria() ?? [])]
            .sort((a, b) => (a.title ?? '').localeCompare(b.title ?? ''))
            .map((criterion) => ({
                title: criterion.title,
                instructions: [...(criterion.structuredGradingInstructions ?? [])].sort((a, b) => (a.instructionDescription ?? '').localeCompare(b.instructionDescription ?? '')),
            })),
    );

    readonly selectable = computed(() => !this.readonly() && this.selectionService.isSelectable());

    readonly appliedCountPerCriterion = computed(() => {
        const applied = this.selectionService.appliedInstructionIds();
        return this.sortedCriteria().map((criterion) => criterion.instructions.filter((instruction) => instruction.id !== undefined && applied.has(instruction.id)).length);
    });

    /**
     * OnInit set the allowDrop property to allow drop of SGI if not in readOnly mode
     */
    ngOnInit(): void {
        this.allowDrop.set(!this.readonly());
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
     * An already applied instruction must not be dragged onto a second target: that would add another feedback for
     * it, which either duplicates the credits or is silently ignored once its usage limit is reached.
     */
    isDraggable(instruction: GradingInstruction): boolean {
        return this.allowDrop() && !this.isApplied(instruction);
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
            deleteQuestion: 'artemisApp.feedback.delete.question',
            translateValues: { text: '' },
            actionType: ActionType.Delete,
            buttonType: ButtonType.ERROR,
            requireConfirmationOnlyForAdditionalChecks: false,
            delete: () => this.selectionService.setApplied(instruction, false),
        });
    }

    /**
     * Set the tooltip of the draggable grading instruction to be equal to the feedback detail text
     * @param {GradingInstruction} instr - the instruction object from which the feedback detail text is retrieved
     */
    setTooltip(instr: GradingInstruction) {
        return 'Feedback: ' + instr.feedback;
    }

    /**
     * Set the color of the draggable grading instruction based on the credits of the instruction
     *  @param {GradingInstruction} instr - the instruction object we set its color based on its credits
     */
    setInstrColour(instr: GradingInstruction) {
        let colour;
        if (instr.credits === 0) {
            colour = 'var(--sgi-assessment-layout-zero-background)';
        } else if (instr.credits < 0) {
            colour = 'var(--sgi-assessment-layout-negative-background)';
        } else {
            colour = 'var(--sgi-assessment-layout-positive-background)';
        }
        return colour;
    }
    setScore(nr: number) {
        return nr + 'P';
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
    disableDrag() {
        return this.allowDrop();
    }
}
