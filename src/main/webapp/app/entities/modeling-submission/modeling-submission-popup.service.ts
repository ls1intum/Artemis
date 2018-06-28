import { Injectable, Component } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { ModelingSubmission } from './modeling-submission.model';
import { ModelingSubmissionService } from './modeling-submission.service';

@Injectable()
export class ModelingSubmissionPopupService {
    private ngbModalRef: NgbModalRef;

    constructor(
        private modalService: NgbModal,
        private router: Router,
        private modelingSubmissionService: ModelingSubmissionService

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
                this.modelingSubmissionService.find(id)
                    .subscribe((modelingSubmissionResponse: HttpResponse<ModelingSubmission>) => {
                        const modelingSubmission: ModelingSubmission = modelingSubmissionResponse.body;
                        this.ngbModalRef = this.modelingSubmissionModalRef(component, modelingSubmission);
                        resolve(this.ngbModalRef);
                    });
            } else {
                // setTimeout used as a workaround for getting ExpressionChangedAfterItHasBeenCheckedError
                setTimeout(() => {
                    this.ngbModalRef = this.modelingSubmissionModalRef(component, new ModelingSubmission());
                    resolve(this.ngbModalRef);
                }, 0);
            }
        });
    }

    modelingSubmissionModalRef(component: Component, modelingSubmission: ModelingSubmission): NgbModalRef {
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static'});
        modalRef.componentInstance.modelingSubmission = modelingSubmission;
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
