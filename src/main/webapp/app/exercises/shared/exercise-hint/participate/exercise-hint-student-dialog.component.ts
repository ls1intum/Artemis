import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
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
    activeModal = inject(NgbActiveModal);

    @Input() availableExerciseHints: ExerciseHint[];
    @Input() activatedExerciseHints: ExerciseHint[];
    @Output()
    onHintActivated = new EventEmitter<ExerciseHint>();

    /**
     * Dismisses the modal
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }
}
