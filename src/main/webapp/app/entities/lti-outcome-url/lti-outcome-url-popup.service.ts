import { Injectable, Component } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { HttpResponse } from '@angular/common/http';
import { LtiOutcomeUrl } from './lti-outcome-url.model';
import { LtiOutcomeUrlService } from './lti-outcome-url.service';

@Injectable()
export class LtiOutcomeUrlPopupService {
    private ngbModalRef: NgbModalRef;

    constructor(
        private modalService: NgbModal,
        private router: Router,
        private ltiOutcomeUrlService: LtiOutcomeUrlService

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
                this.ltiOutcomeUrlService.find(id)
                    .subscribe((ltiOutcomeUrlResponse: HttpResponse<LtiOutcomeUrl>) => {
                        const ltiOutcomeUrl: LtiOutcomeUrl = ltiOutcomeUrlResponse.body;
                        this.ngbModalRef = this.ltiOutcomeUrlModalRef(component, ltiOutcomeUrl);
                        resolve(this.ngbModalRef);
                    });
            } else {
                // setTimeout used as a workaround for getting ExpressionChangedAfterItHasBeenCheckedError
                setTimeout(() => {
                    this.ngbModalRef = this.ltiOutcomeUrlModalRef(component, new LtiOutcomeUrl());
                    resolve(this.ngbModalRef);
                }, 0);
            }
        });
    }

    ltiOutcomeUrlModalRef(component: Component, ltiOutcomeUrl: LtiOutcomeUrl): NgbModalRef {
        const modalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static'});
        modalRef.componentInstance.ltiOutcomeUrl = ltiOutcomeUrl;
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
