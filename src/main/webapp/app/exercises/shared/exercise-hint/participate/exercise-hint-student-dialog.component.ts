import { Component, EventEmitter, Input, Output } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';

/**
 * This component is a modal that shows the exercise's hints.
 */
@Component({
    selector: 'jhi-exercise-hint-student-dialog',
    templateUrl: './exercise-hint-student-dialog.component.html',
})
export class ExerciseHintStudentDialogComponent {
    @Input() availableExerciseHints: ExerciseHint[];
    @Input() activatedExerciseHints: ExerciseHint[];
    @Output()
    onHintActivated = new EventEmitter<ExerciseHint>();

    constructor(public activeModal: NgbActiveModal) {}

    /**
     * Dismisses the modal
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }
}
