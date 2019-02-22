import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { ProgrammingExercise } from './programming-exercise.model';
import { ProgrammingExerciseService } from './programming-exercise.service';

import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-programming-exercise-delete-dialog',
    templateUrl: './programming-exercise-delete-dialog.component.html'
})
export class ProgrammingExerciseDeleteDialogComponent {

    programmingExercise: ProgrammingExercise;
    confirmExerciseName: string;
    deleteBaseReposBuildPlans = false;
    deleteStudentReposBuildPlans = true;
    deleteInProgress = false;

    constructor(
        private programmingExerciseService: ProgrammingExerciseService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager,
        private jhiAlertService: JhiAlertService
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.deleteInProgress = true;
        this.programmingExerciseService.delete(id, this.deleteStudentReposBuildPlans, this.deleteBaseReposBuildPlans).subscribe(
            response => {
                this.deleteInProgress = false;
                this.eventManager.broadcast({
                    name: 'programmingExerciseListModification',
                    content: 'Deleted an programmingExercise'
                });
                // this.jhiAlertService.success('Delete was successful');
                this.activeModal.dismiss(true);
            },
            (error: HttpErrorResponse) => {
                this.jhiAlertService.error(error.message);
                this.deleteInProgress = false;
            }
        );
    }
}

@Component({
    selector: 'jhi-programming-exercise-delete-popup',
    template: ''
})
export class ProgrammingExerciseDeletePopupComponent implements OnInit, OnDestroy {
    protected ngbModalRef: NgbModalRef;

    constructor(protected activatedRoute: ActivatedRoute, protected router: Router, protected modalService: NgbModal) {}

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
