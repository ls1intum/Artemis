import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IProgrammingExercise } from 'app/shared/model/programming-exercise.model';
import { ProgrammingExerciseService } from './programming-exercise.service';

@Component({
    selector: 'jhi-programming-exercise-delete-dialog',
    templateUrl: './programming-exercise-delete-dialog.component.html'
})
export class ProgrammingExerciseDeleteDialogComponent {
    programmingExercise: IProgrammingExercise;

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.programmingExerciseService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'programmingExerciseListModification',
                content: 'Deleted an programmingExercise'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-programming-exercise-delete-popup',
    template: ''
})
export class ProgrammingExerciseDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ programmingExercise }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(ProgrammingExerciseDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.programmingExercise = programmingExercise;
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
