import { Component, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { QuizExercise } from './quiz-exercise.model';
import { QuizExerciseService } from './quiz-exercise.service';

@Injectable({ providedIn: 'root' })
export class QuizExercisePopupService {
    private ngbModalRef: NgbModalRef;

    constructor(private modalService: NgbModal, private router: Router, private quizExerciseService: QuizExerciseService) {
        this.ngbModalRef = null;
    }

    open(component: Component, id?: string | object | any): Promise<NgbModalRef> {
        return new Promise<NgbModalRef>((resolve, reject) => {
            const isOpen = this.ngbModalRef !== null;
            if (isOpen) {
                resolve(this.ngbModalRef);
            }

            // For re-evaluate id parameter is of type QuizExercise - regardless this fact instanceof QuizExercise does not work
            // the type check instanceof Object is not redundant (as intellij might want to tell you)
            if (id instanceof Object) {
                this.ngbModalRef = this.quizExerciseModalRef(component, id);
                resolve(this.ngbModalRef);
            } else if (id) {
                this.quizExerciseService.find(id).subscribe((quizExerciseResponse: HttpResponse<QuizExercise>) => {
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
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.quizExercise = quizExercise;
        modalRef.result.then(
            result => {
                if (result === 're-evaluate') {
                    this.router.navigate(['/course/' + quizExercise.course.id + '/quiz-exercise']);
                } else {
                    this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                    this.ngbModalRef = null;
                }
            },
            reason => {
                this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                this.ngbModalRef = null;
            }
        );
        return modalRef;
    }
}
