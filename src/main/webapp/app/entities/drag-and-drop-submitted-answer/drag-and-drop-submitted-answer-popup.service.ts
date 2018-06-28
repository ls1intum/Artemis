import { Injectable, Component } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { DragAndDropSubmittedAnswer } from './drag-and-drop-submitted-answer.model';
import { DragAndDropSubmittedAnswerService } from './drag-and-drop-submitted-answer.service';

@Injectable()
export class DragAndDropSubmittedAnswerPopupService {
    private ngbModalRef: NgbModalRef;

    constructor(
        private modalService: NgbModal,
        private router: Router,
        private dragAndDropSubmittedAnswerService: DragAndDropSubmittedAnswerService

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
                this.dragAndDropSubmittedAnswerService.find(id)
                    .subscribe((dragAndDropSubmittedAnswerResponse: HttpResponse<DragAndDropSubmittedAnswer>) => {
                        const dragAndDropSubmittedAnswer: DragAndDropSubmittedAnswer = dragAndDropSubmittedAnswerResponse.body;
                        this.ngbModalRef = this.dragAndDropSubmittedAnswerModalRef(component, dragAndDropSubmittedAnswer);
                        resolve(this.ngbModalRef);
                    });
            } else {
                // setTimeout used as a workaround for getting ExpressionChangedAfterItHasBeenCheckedError
                setTimeout(() => {
                    this.ngbModalRef = this.dragAndDropSubmittedAnswerModalRef(component, new DragAndDropSubmittedAnswer());
                    resolve(this.ngbModalRef);
                }, 0);
            }
        });
    }

    dragAndDropSubmittedAnswerModalRef(component: Component, dragAndDropSubmittedAnswer: DragAndDropSubmittedAnswer): NgbModalRef {
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static'});
        modalRef.componentInstance.dragAndDropSubmittedAnswer = dragAndDropSubmittedAnswer;
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
