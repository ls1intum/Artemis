import { Injectable, Component } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { MultipleChoiceQuestion } from './multiple-choice-question.model';
import { MultipleChoiceQuestionService } from './multiple-choice-question.service';

@Injectable()
export class MultipleChoiceQuestionPopupService {
    private ngbModalRef: NgbModalRef;

    constructor(
        private modalService: NgbModal,
        private router: Router,
        private multipleChoiceQuestionService: MultipleChoiceQuestionService

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
                this.multipleChoiceQuestionService.find(id)
                    .subscribe((multipleChoiceQuestionResponse: HttpResponse<MultipleChoiceQuestion>) => {
                        const multipleChoiceQuestion: MultipleChoiceQuestion = multipleChoiceQuestionResponse.body;
                        this.ngbModalRef = this.multipleChoiceQuestionModalRef(component, multipleChoiceQuestion);
                        resolve(this.ngbModalRef);
                    });
            } else {
                // setTimeout used as a workaround for getting ExpressionChangedAfterItHasBeenCheckedError
                setTimeout(() => {
                    this.ngbModalRef = this.multipleChoiceQuestionModalRef(component, new MultipleChoiceQuestion());
                    resolve(this.ngbModalRef);
                }, 0);
            }
        });
    }

    multipleChoiceQuestionModalRef(component: Component, multipleChoiceQuestion: MultipleChoiceQuestion): NgbModalRef {
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static'});
        modalRef.componentInstance.multipleChoiceQuestion = multipleChoiceQuestion;
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
