import { Injectable, Component } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { QuizExercise } from './quiz-exercise.model';
import { QuizExerciseService } from './quiz-exercise.service';

@Injectable()
export class QuizExercisePopupService {
    private ngbModalRef: NgbModalRef;

    constructor(
        private modalService: NgbModal,
        private router: Router,
        private quizExerciseService: QuizExerciseService

    ) {
        this.ngbModalRef = null;
    }

    open(component: Component, id?: number | any): Promise<NgbModalRef> {
        return new Promise<NgbModalRef>((resolve, reject) => {
            const isOpen = this.ngbModalRef !== null;
            if (isOpen) {
                resolve(this.ngbModalRef);
            }

            if (id) {
                this.quizExerciseService.find(id)
                    .subscribe((quizExerciseResponse: HttpResponse<QuizExercise>) => {
                        const quizExercise: QuizExercise = quizExerciseResponse.body;
                        this.ngbModalRef = this.quizExerciseModalRef(component, quizExercise);
                        resolve(this.ngbModalRef);
                    });
            } else {
                // setTimeout used as a workaround for getting ExpressionChangedAfterItHasBeenCheckedError
                setTimeout(() => {
                    this.ngbModalRef = this.quizExerciseModalRef(component, new QuizExercise());
                    resolve(this.ngbModalRef);
                }, 0);
            }
        });
    }

    quizExerciseModalRef(component: Component, quizExercise: QuizExercise): NgbModalRef {
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static'});
        modalRef.componentInstance.quizExercise = quizExercise;
        modalRef.result.then((result) => {
            this.router.navigate([{ outlets: { popup: null }}], { replaceUrl: true, queryParamsHandling: 'merge' });
            this.ngbModalRef = null;
        }, (reason) => {
            this.router.navigate([{ outlets: { popup: null }}], { replaceUrl: true, queryParamsHandling: 'merge' });
            this.ngbModalRef = null;
        });
        return modalRef;
    }
}
