import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { ModelingExercise } from './modeling-exercise.model';
import { ModelingExercisePopupService } from './modeling-exercise-popup.service';
import { ModelingExerciseService } from './modeling-exercise.service';

import { Subscription } from 'rxjs/Subscription';

@Component({
    selector: 'jhi-modeling-exercise-delete-dialog',
    templateUrl: './modeling-exercise-delete-dialog.component.html'
})
export class ModelingExerciseDeleteDialogComponent {
    modelingExercise: ModelingExercise;

    constructor(
        private modelingExerciseService: ModelingExerciseService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.modelingExerciseService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'modelingExerciseListModification',
                content: 'Deleted an modelingExercise'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-modeling-exercise-delete-popup',
    template: ''
})
export class ModelingExerciseDeletePopupComponent implements OnInit, OnDestroy {
    routeSub: Subscription;

    constructor(private route: ActivatedRoute, private modelingExercisePopupService: ModelingExercisePopupService) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            this.modelingExercisePopupService.open(ModelingExerciseDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
