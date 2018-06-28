import { Injectable, Component } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { DragAndDropAssignment } from './drag-and-drop-assignment.model';
import { DragAndDropAssignmentService } from './drag-and-drop-assignment.service';

@Injectable()
export class DragAndDropAssignmentPopupService {
    private ngbModalRef: NgbModalRef;

    constructor(
        private modalService: NgbModal,
        private router: Router,
        private dragAndDropAssignmentService: DragAndDropAssignmentService

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
                this.dragAndDropAssignmentService.find(id)
                    .subscribe((dragAndDropAssignmentResponse: HttpResponse<DragAndDropAssignment>) => {
                        const dragAndDropAssignment: DragAndDropAssignment = dragAndDropAssignmentResponse.body;
                        this.ngbModalRef = this.dragAndDropAssignmentModalRef(component, dragAndDropAssignment);
                        resolve(this.ngbModalRef);
                    });
            } else {
                // setTimeout used as a workaround for getting ExpressionChangedAfterItHasBeenCheckedError
                setTimeout(() => {
                    this.ngbModalRef = this.dragAndDropAssignmentModalRef(component, new DragAndDropAssignment());
                    resolve(this.ngbModalRef);
                }, 0);
            }
        });
    }

    dragAndDropAssignmentModalRef(component: Component, dragAndDropAssignment: DragAndDropAssignment): NgbModalRef {
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static'});
        modalRef.componentInstance.dragAndDropAssignment = dragAndDropAssignment;
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
