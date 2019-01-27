import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { QuizExercise } from './quiz-exercise.model';
import { QuizExerciseService } from './quiz-exercise.service';

import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-quiz-exercise-reset-dialog',
    templateUrl: './quiz-exercise-reset-dialog.component.html'
})
export class QuizExerciseResetDialogComponent {

    quizExercise: QuizExercise;
    confirmExerciseName: string;
    resetInProgress = false;

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

    confirmReset(id: number) {
        this.resetInProgress = true;
        this.quizExerciseService.reset(id).subscribe(
        response => {
            this.resetInProgress = false;
            this.eventManager.broadcast({
                name: 'quizExerciseListModification',
                content: 'Reset an quizExercise'
            });
            this.activeModal.dismiss(true);
        },
        (error: HttpErrorResponse) => {
            this.jhiAlertService.error(error.message);
            this.resetInProgress = false;
        });
    }
}

@Component({
    selector: 'jhi-quiz-exercise-reset-popup',
    template: ''
})
export class QuizExerciseResetPopupComponent implements OnInit, OnDestroy {
    protected ngbModalRef: NgbModalRef;

    constructor(protected activatedRoute: ActivatedRoute, protected router: Router, protected modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ quizExercise }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(QuizExerciseResetDialogComponent as Component, {
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
