import { Component, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { QuizExerciseService } from './quiz-exercise.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';

@Injectable({ providedIn: 'root' })
export class QuizExercisePopupService {
    private ngbModalRef: NgbModalRef | null;

    constructor(private modalService: NgbModal, private router: Router, private quizExerciseService: QuizExerciseService) {
        this.ngbModalRef = null;
    }

    open(component: Component, quizExercise: QuizExercise): Promise<NgbModalRef> {
        return new Promise<NgbModalRef>((resolve, reject) => {
            if (this.ngbModalRef == null) {
                this.ngbModalRef = this.quizExerciseModalRef(component, quizExercise);
            }
            resolve(this.ngbModalRef);
        });
    }

    quizExerciseModalRef(component: Component, quizExercise: QuizExercise): NgbModalRef {
        const modalRef: NgbModalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.quizExercise = quizExercise;
        modalRef.result.then(
            result => {
                if (result === 're-evaluate') {
                    this.router.navigate(['/course-management/' + quizExercise.course!.id + '/quiz-exercises']);
                } else {
                    this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                    this.ngbModalRef = null;
                }
            },
            reason => {
                this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                this.ngbModalRef = null;
            },
        );
        return modalRef;
    }
}
