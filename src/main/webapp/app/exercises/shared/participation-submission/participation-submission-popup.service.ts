import { Component, Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';

@Injectable({ providedIn: 'root' })
export class ParticipationSubmissionPopupService {
    private ngbModalRef: NgbModalRef | null;

    constructor(private modalService: NgbModal, private router: Router) {
        this.ngbModalRef = null;
    }

    /**
     * Open Modal window if it already exists, otherwise create new one and open it
     * @param component - Component that should be displayed inside of the modal
     * @param participationId - Id of participation that is displayed
     * @param submissionId - Id of submission that is displayed
     */
    open(component: Component, participationId?: number | any, submissionId?: number | any): Promise<NgbModalRef> {
        return new Promise<NgbModalRef>((resolve) => {
            if (this.ngbModalRef != null) {
                resolve(this.ngbModalRef);
            }

            if (participationId && submissionId) {
                this.ngbModalRef = this.participationModalRef(component, participationId, submissionId);
                resolve(this.ngbModalRef);
            }
        });
    }

    /**
     * Create a new modal reference
     * @param component - Component that should be displayed inside of the modal
     * @param participationId - Id of participation that is displayed
     * @param submissionId - Id of submission that is displayed
     * @return created modal reference
     */
    participationModalRef(component: Component, participationId: number, submissionId: number): NgbModalRef {
        const modalRef: NgbModalRef = this.modalService.open(component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.participationId = participationId;
        modalRef.componentInstance.submissionId = submissionId;
        modalRef.result.then(
            () => {
                this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                this.ngbModalRef = null;
            },
            () => {
                this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                this.ngbModalRef = null;
            },
        );
        return modalRef;
    }
}
