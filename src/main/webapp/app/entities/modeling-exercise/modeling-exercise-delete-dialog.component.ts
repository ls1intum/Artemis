import { Component } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';
import { ModelingExercise } from './modeling-exercise.model';
import { ModelingExerciseService } from './modeling-exercise.service';

@Component({
    selector: 'jhi-modeling-exercise-delete-dialog',
    templateUrl: './modeling-exercise-delete-dialog.component.html',
})
export class ModelingExerciseDeleteDialogComponent {
    modelingExercise: ModelingExercise;
    confirmExerciseName: string;

    constructor(private modelingExerciseService: ModelingExerciseService, public activeModal: NgbActiveModal, private eventManager: JhiEventManager) {}

    /**
     * Closes the dialog
     */
    clear() {
        this.activeModal.dismiss('cancel');
    }

    /**
     * Deletes specified modeling exercise and closes the dialog
     * @param exerciseId
     */
    confirmDelete(id: number) {
        this.modelingExerciseService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'modelingExerciseListModification',
                content: 'Deleted an modelingExercise',
            });
            this.activeModal.dismiss(true);
        });
    }
}
