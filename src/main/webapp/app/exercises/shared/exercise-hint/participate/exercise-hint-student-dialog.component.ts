import { Component, Input, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';

/**
 * This component is a modal that shows the exercise's hints.
 */
@Component({
    selector: 'jhi-exercise-hint-student-dialog',
    templateUrl: './exercise-hint-student-dialog.component.html',
})
export class ExerciseHintStudentDialogComponent implements OnInit {
    @Input() exerciseHints: ExerciseHint[];

    newAvailableExerciseHints: ExerciseHint[];
    activatedExerciseHints: ExerciseHint[];

    constructor(public activeModal: NgbActiveModal) {}

    ngOnInit(): void {
        this.categorizeExerciseHints();
    }

    categorizeExerciseHints() {
        this.newAvailableExerciseHints = this.exerciseHints.filter((hint) => !hint.hasUsed);
        this.activatedExerciseHints = this.exerciseHints.filter((hint) => hint.hasUsed);
    }

    /**
     * Dismisses the modal
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }
}
