import { Injectable, Component } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { QuizSubmission } from './quiz-submission.model';
import { QuizSubmissionService } from './quiz-submission.service';

@Injectable()
export class QuizSubmissionPopupService {
    private ngbModalRef: NgbModalRef;

    constructor(
        private modalService: NgbModal,
        private router: Router,
        private quizSubmissionService: QuizSubmissionService

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
                this.quizSubmissionService.find(id)
                    .subscribe((quizSubmissionResponse: HttpResponse<QuizSubmission>) => {
                        const quizSubmission: QuizSubmission = quizSubmissionResponse.body;
                        this.ngbModalRef = this.quizSubmissionModalRef(component, quizSubmission);
                        resolve(this.ngbModalRef);
                    });
            } else {
                // setTimeout used as a workaround for getting ExpressionChangedAfterItHasBeenCheckedError
                setTimeout(() => {
                    this.ngbModalRef = this.quizSubmissionModalRef(component, new QuizSubmission());
                    resolve(this.ngbModalRef);
                }, 0);
            }
        });
    }

    quizSubmissionModalRef(component: Component, quizSubmission: QuizSubmission): NgbModalRef {
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static'});
        modalRef.componentInstance.quizSubmission = quizSubmission;
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
