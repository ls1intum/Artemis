import { Injectable, Component } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { SubmittedAnswer } from './submitted-answer.model';
import { SubmittedAnswerService } from './submitted-answer.service';

@Injectable()
export class SubmittedAnswerPopupService {
    private ngbModalRef: NgbModalRef;

    constructor(
        private modalService: NgbModal,
        private router: Router,
        private submittedAnswerService: SubmittedAnswerService

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
                this.submittedAnswerService.find(id)
                    .subscribe((submittedAnswerResponse: HttpResponse<SubmittedAnswer>) => {
                        const submittedAnswer: SubmittedAnswer = submittedAnswerResponse.body;
                        this.ngbModalRef = this.submittedAnswerModalRef(component, submittedAnswer);
                        resolve(this.ngbModalRef);
                    });
            } else {
                // setTimeout used as a workaround for getting ExpressionChangedAfterItHasBeenCheckedError
                setTimeout(() => {
                    this.ngbModalRef = this.submittedAnswerModalRef(component, new SubmittedAnswer());
                    resolve(this.ngbModalRef);
                }, 0);
            }
        });
    }

    submittedAnswerModalRef(component: Component, submittedAnswer: SubmittedAnswer): NgbModalRef {
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static'});
        modalRef.componentInstance.submittedAnswer = submittedAnswer;
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
