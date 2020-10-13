import { Component } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IExerciseResult } from 'app/shared/model/exercise-result.model';
import { ExerciseResultService } from './exercise-result.service';

@Component({
    templateUrl: './exercise-result-delete-dialog.component.html',
})
export class ExerciseResultDeleteDialogComponent {
    exerciseResult?: IExerciseResult;

    constructor(protected exerciseResultService: ExerciseResultService, public activeModal: NgbActiveModal, protected eventManager: JhiEventManager) {}

    cancel(): void {
        this.activeModal.dismiss();
    }

    confirmDelete(id: number): void {
        this.exerciseResultService.delete(id).subscribe(() => {
            this.eventManager.broadcast('exerciseResultListModification');
            this.activeModal.close();
        });
    }
}
