import { Injectable, Component } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { MultipleChoiceSubmittedAnswer } from './multiple-choice-submitted-answer.model';
import { MultipleChoiceSubmittedAnswerService } from './multiple-choice-submitted-answer.service';

@Injectable()
export class MultipleChoiceSubmittedAnswerPopupService {
    private ngbModalRef: NgbModalRef;

    constructor(
        private modalService: NgbModal,
        private router: Router,
        private multipleChoiceSubmittedAnswerService: MultipleChoiceSubmittedAnswerService

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
                this.multipleChoiceSubmittedAnswerService.find(id)
                    .subscribe((multipleChoiceSubmittedAnswerResponse: HttpResponse<MultipleChoiceSubmittedAnswer>) => {
                        const multipleChoiceSubmittedAnswer: MultipleChoiceSubmittedAnswer = multipleChoiceSubmittedAnswerResponse.body;
                        this.ngbModalRef = this.multipleChoiceSubmittedAnswerModalRef(component, multipleChoiceSubmittedAnswer);
                        resolve(this.ngbModalRef);
                    });
            } else {
                // setTimeout used as a workaround for getting ExpressionChangedAfterItHasBeenCheckedError
                setTimeout(() => {
                    this.ngbModalRef = this.multipleChoiceSubmittedAnswerModalRef(component, new MultipleChoiceSubmittedAnswer());
                    resolve(this.ngbModalRef);
                }, 0);
            }
        });
    }

    multipleChoiceSubmittedAnswerModalRef(component: Component, multipleChoiceSubmittedAnswer: MultipleChoiceSubmittedAnswer): NgbModalRef {
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static'});
        modalRef.componentInstance.multipleChoiceSubmittedAnswer = multipleChoiceSubmittedAnswer;
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
