import { Injectable, Component } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { DragAndDropQuestion } from './drag-and-drop-question.model';
import { DragAndDropQuestionService } from './drag-and-drop-question.service';

@Injectable()
export class DragAndDropQuestionPopupService {
    private ngbModalRef: NgbModalRef;

    constructor(
        private modalService: NgbModal,
        private router: Router,
        private dragAndDropQuestionService: DragAndDropQuestionService

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
                this.dragAndDropQuestionService.find(id)
                    .subscribe((dragAndDropQuestionResponse: HttpResponse<DragAndDropQuestion>) => {
                        const dragAndDropQuestion: DragAndDropQuestion = dragAndDropQuestionResponse.body;
                        this.ngbModalRef = this.dragAndDropQuestionModalRef(component, dragAndDropQuestion);
                        resolve(this.ngbModalRef);
                    });
            } else {
                // setTimeout used as a workaround for getting ExpressionChangedAfterItHasBeenCheckedError
                setTimeout(() => {
                    this.ngbModalRef = this.dragAndDropQuestionModalRef(component, new DragAndDropQuestion());
                    resolve(this.ngbModalRef);
                }, 0);
            }
        });
    }

    dragAndDropQuestionModalRef(component: Component, dragAndDropQuestion: DragAndDropQuestion): NgbModalRef {
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static'});
        modalRef.componentInstance.dragAndDropQuestion = dragAndDropQuestion;
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
