import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { ILtiUserId } from 'app/shared/model/lti-user-id.model';
import { LtiUserIdService } from './lti-user-id.service';

@Component({
    selector: 'jhi-lti-user-id-delete-dialog',
    templateUrl: './lti-user-id-delete-dialog.component.html'
})
export class LtiUserIdDeleteDialogComponent {
    ltiUserId: ILtiUserId;

    constructor(private ltiUserIdService: LtiUserIdService, public activeModal: NgbActiveModal, private eventManager: JhiEventManager) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.ltiUserIdService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'ltiUserIdListModification',
                content: 'Deleted an ltiUserId'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-lti-user-id-delete-popup',
    template: ''
})
export class LtiUserIdDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ ltiUserId }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(LtiUserIdDeleteDialogComponent as Component, { size: 'lg', backdrop: 'static' });
                this.ngbModalRef.componentInstance.ltiUserId = ltiUserId;
                this.ngbModalRef.result.then(
                    result => {
                        this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                        this.ngbModalRef = null;
                    },
                    reason => {
                        this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                        this.ngbModalRef = null;
                    }
                );
            }, 0);
        });
    }

    ngOnDestroy() {
        this.ngbModalRef = null;
    }
}
