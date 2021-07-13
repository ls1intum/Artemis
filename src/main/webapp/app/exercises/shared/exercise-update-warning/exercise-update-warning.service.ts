import { Component, Injectable } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Exercise } from 'app/entities/exercise.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { ExerciseUpdateWarningComponent } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.component';

@Injectable({ providedIn: 'root' })
export class ExerciseUpdateWarningService {
    private ngbModalRef: NgbModalRef;

    instructionDeleted: boolean;
    creditChanged: boolean;
    usageCountChanged: boolean;
    isSaving: boolean;

    constructor(private modalService: NgbModal) {}

    /**
     * Open the modal with the given content for the given exercise.
     * @param component the content that should be shown
     */
    open(component: Component): NgbModalRef {
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.instructionDeleted = this.instructionDeleted;
        modalRef.componentInstance.creditChanged = this.creditChanged;
        modalRef.componentInstance.usageCountChanged = this.usageCountChanged;

        return modalRef;
    }

    /**
     * check if the changes affect the existing results
     *  if it affects the results, then open warning modal before updating the exercise
     *
     * @param exercise the exercise for which the modal should be shown
     * @param backupExercise the copy of exercise for which the modal should be shown
     */
    checkExerciseBeforeUpdate(exercise: Exercise, backupExercise: Exercise): Promise<NgbModalRef> {
        this.instructionDeleted = false;
        this.creditChanged = false;
        this.usageCountChanged = false;
        this.loadExercise(exercise, backupExercise);
        return new Promise<NgbModalRef>((resolve) => {
            if (this.creditChanged || this.instructionDeleted || this.usageCountChanged) {
                this.ngbModalRef = this.open(ExerciseUpdateWarningComponent as Component);
            }
            resolve(this.ngbModalRef);
        });
    }

    /**
     * check if the changes affect the existing results
     *  1. check if a grading criterion is deleted
     *  2. check for each grading instruction if:
     *          - it is deleted
     *          - the credit is changed
     *          - usage count is changed
     *
     * @param exercise the exercise for which the modal should be shown
     * @param backupExercise the copy of exercise for which the modal should be shown
     */
    loadExercise(exercise: Exercise, backupExercise: Exercise): void {
        // check each grading criterion
        backupExercise.gradingCriteria!.forEach((backupCriterion) => {
            // find same grading criterion in backup exercise (necessary if the order has been changed)
            const updatedCriterion = exercise.gradingCriteria?.find((criterion) => criterion.id === backupCriterion.id);

            if (updatedCriterion) {
                backupCriterion.structuredGradingInstructions?.forEach((backupGradingInstruction) => {
                    const updatedGradingInstruction = updatedCriterion.structuredGradingInstructions?.find(
                        (gradingInstruction) => gradingInstruction.id === backupGradingInstruction.id,
                    );
                    if (updatedGradingInstruction) {
                        this.checkGradingInstruction(updatedGradingInstruction, backupGradingInstruction);
                    } else {
                        this.instructionDeleted = true;
                    }
                });
            } else {
                this.instructionDeleted = true;
            }
        });
    }

    /**
     * 1. compare backupInstruction and instruction
     * 2. set flags based on detected changes
     *
     * @param instruction changed instruction
     * @param backupInstruction original not changed instruction
     */
    checkGradingInstruction(gradingInstruction: GradingInstruction, backupGradingInstruction: GradingInstruction): void {
        // checking whether structured grading instruction credits or usageCount changed
        if (gradingInstruction.credits !== backupGradingInstruction.credits) {
            this.creditChanged = true;
        }
        if (gradingInstruction.usageCount !== backupGradingInstruction.usageCount) {
            this.usageCountChanged = true;
        }
    }
}
