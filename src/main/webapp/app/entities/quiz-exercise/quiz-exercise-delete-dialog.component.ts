import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';

import { QuizExercise } from './quiz-exercise.model';
import { QuizExerciseService } from './quiz-exercise.service';

import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-quiz-exercise-delete-dialog',
    templateUrl: './quiz-exercise-delete-dialog.component.html'
})
export class QuizExerciseDeleteDialogComponent {

    quizExercise: QuizExercise;
    confirmExerciseName: string;
    deleteInProgress = false;

    constructor(
        private quizExerciseService: QuizExerciseService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager,
        private jhiAlertService: JhiAlertService
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.deleteInProgress = true;
        this.quizExerciseService.delete(id).subscribe(
        response => {
            this.deleteInProgress = false;
            this.eventManager.broadcast({
                name: 'quizExerciseListModification',
                content: 'Deleted an quizExercise'
            });
            this.activeModal.dismiss(true);
        },
        (error: HttpErrorResponse) => {
            this.jhiAlertService.error(error.message);
            this.deleteInProgress = false;
        });
    }
}

@Component({
    selector: 'jhi-quiz-exercise-delete-popup',
    template: ''
})
export class QuizExerciseDeletePopupComponent implements OnInit, OnDestroy {
    protected ngbModalRef: NgbModalRef;

    constructor(protected activatedRoute: ActivatedRoute, protected router: Router, protected modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ quizExercise }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(QuizExerciseDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.quizExercise = quizExercise;
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
