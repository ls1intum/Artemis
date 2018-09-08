import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IModelingExercise } from 'app/shared/model/modeling-exercise.model';
import { ModelingExerciseService } from './modeling-exercise.service';

@Component({
    selector: 'jhi-modeling-exercise-delete-dialog',
    templateUrl: './modeling-exercise-delete-dialog.component.html'
})
export class ModelingExerciseDeleteDialogComponent {
    modelingExercise: IModelingExercise;

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
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ modelingExercise }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(ModelingExerciseDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.modelingExercise = modelingExercise;
                this.ngbModalRef.result.then(
                    result => {
                        this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                        this.ngbModalRef = null;
                    },
                    reason => {
                        this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                        this.ngbModalRef = null;
                    }
                );
            }, 0);
        });
    }

    ngOnDestroy() {
        this.ngbModalRef = null;
    }
}
