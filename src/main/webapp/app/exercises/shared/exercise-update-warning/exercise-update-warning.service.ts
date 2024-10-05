import { Component, Injectable, inject } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { Exercise } from 'app/entities/exercise.model';
import { GradingInstruction } from 'app/exercises/shared/structured-grading-criterion/grading-instruction.model';
import { ExerciseUpdateWarningComponent } from 'app/exercises/shared/exercise-update-warning/exercise-update-warning.component';
import dayjs from 'dayjs/esm';

@Injectable({ providedIn: 'root' })
export class ExerciseUpdateWarningService {
    private modalService = inject(NgbModal);

    private ngbModalRef: NgbModalRef;

    instructionDeleted: boolean;
    creditChanged: boolean;
    usageCountChanged: boolean;
    immediateReleaseWarning: string;
    isSaving: boolean;

    isExamMode: boolean;

    /**
     * Open the modal with the given content for the given exercise.
     * @param component the content that should be shown
     */
    open(component: Component): NgbModalRef {
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.instructionDeleted = this.instructionDeleted;
        modalRef.componentInstance.creditChanged = this.creditChanged;
        modalRef.componentInstance.usageCountChanged = this.usageCountChanged;
        modalRef.componentInstance.immediateReleaseWarning = this.immediateReleaseWarning;

        return modalRef;
    }

    /**
     * check if there might be unwanted changes and inform the instructor by opening warning modal before updating the exercise
     *
     * @param exercise the exercise for which the modal should be shown
     * @param backupExercise the copy of exercise for which the modal should be shown
     * @param isExamMode flag that indicates if the exercise is part of an exam
     */
    checkExerciseBeforeUpdate(exercise: Exercise, backupExercise: Exercise, isExamMode: boolean): Promise<NgbModalRef> {
        if (exercise.course?.testCourse) {
            return new Promise<NgbModalRef>((resolve) => resolve(this.ngbModalRef));
        }

        this.initializeVariables(isExamMode);
        this.loadExercise(exercise, backupExercise);
        this.checkImmediateRelease(exercise, backupExercise);
        return new Promise<NgbModalRef>((resolve) => {
            if (this.creditChanged || this.instructionDeleted || this.usageCountChanged || this.immediateReleaseWarning) {
                this.ngbModalRef = this.open(ExerciseUpdateWarningComponent as Component);
            }
            resolve(this.ngbModalRef);
        });
    }

    /**
     * Resets all possible warnings and checks if the exercise is part of an exam
     */
    initializeVariables(isExamMode: boolean) {
        this.instructionDeleted = false;
        this.creditChanged = false;
        this.usageCountChanged = false;
        this.immediateReleaseWarning = '';
        this.isExamMode = isExamMode;
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
        backupExercise.gradingCriteria?.forEach((backupCriterion) => {
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
     * @param gradingInstruction changed instruction
     * @param backupGradingInstruction original not changed instruction
     */
    checkGradingInstruction(gradingInstruction: GradingInstruction, backupGradingInstruction: GradingInstruction): void {
        // checking whether structured grading instruction credits or usageCount changed
        this.creditChanged = gradingInstruction.credits !== backupGradingInstruction.credits;
        this.usageCountChanged = gradingInstruction.usageCount !== backupGradingInstruction.usageCount;
    }

    /**
     * Checks if the exercise will be released immediately to students and if students did not see the exercise before
     *
     * @param exercise the updated exercise
     * @param backupExercise the optional exercise before the update that might already be released
     */
    checkImmediateRelease(exercise: Exercise, backupExercise: Exercise) {
        const noReleaseDate = !exercise.releaseDate || !dayjs(exercise.releaseDate).isValid();
        const creationOrReleaseDateBefore = !exercise.id || (backupExercise.releaseDate && dayjs(backupExercise.releaseDate).isValid());
        if (noReleaseDate && !this.isExamMode && creationOrReleaseDateBefore) {
            this.immediateReleaseWarning = exercise.startDate ? 'artemisApp.exercise.noReleaseDateWarning' : 'artemisApp.exercise.noReleaseAndStartDateWarning';
        }
    }
}
