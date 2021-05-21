import { Component, Injectable } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Exercise } from 'app/entities/exercise.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { ExerciseUpdateWarningComponent } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.component';

@Injectable({ providedIn: 'root' })
export class ExerciseUpdateWarningService {
    private ngbModalRef: NgbModalRef;

    instructionDeleted: boolean;
    scoringChanged: boolean;
    isSaving: boolean;

    constructor(private modalService: NgbModal) {}

    /**
     * Open the modal with the given content for the given exercise.
     * @param component the content that should be shown
     */
    open(component: Component): NgbModalRef {
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.instructionDeleted = this.instructionDeleted;
        modalRef.componentInstance.scoringChanged = this.scoringChanged;

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
        this.scoringChanged = false;
        this.loadExercise(exercise, backupExercise);
        return new Promise<NgbModalRef>((resolve) => {
            if (this.scoringChanged || this.instructionDeleted) {
                this.ngbModalRef = this.open(ExerciseUpdateWarningComponent as Component);
            }
            resolve(this.ngbModalRef);
        });
    }

    /**
     * check if the changes affect the existing results
     *  1. check if a grading criterion is deleted
     *  2. check for each instruction if:
     *          - it is deleted
     *          - the credit is changed
     *
     * @param exercise the exercise for which the modal should be shown
     * @param backupExercise the copy of exercise for which the modal should be shown
     */
    loadExercise(exercise: Exercise, backupExercise: Exercise): void {
        // check each instruction
        backupExercise.gradingCriteria!.forEach((criteriaBackup) => {
            // find same question in backUp (necessary if the order has been changed)
            const updatedCriteria = exercise.gradingCriteria?.find((criteria) => criteria.id === criteriaBackup.id);

            if (updatedCriteria) {
                criteriaBackup.structuredGradingInstructions.forEach((instructionBackup) => {
                    const updatedInstruction = updatedCriteria.structuredGradingInstructions?.find((instruction) => instruction.id === instructionBackup.id);
                    if (updatedInstruction) {
                        this.checkInstruction(updatedInstruction, instructionBackup);
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
    checkInstruction(instruction: GradingInstruction, backupInstruction: GradingInstruction): void {
        // instruction credits changed?
        if (instruction.credits !== backupInstruction.credits) {
            this.scoringChanged = true;
        }
    }
}
