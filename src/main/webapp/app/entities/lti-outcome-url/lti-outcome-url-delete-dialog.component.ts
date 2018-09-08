import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { ILtiOutcomeUrl } from 'app/shared/model/lti-outcome-url.model';
import { LtiOutcomeUrlService } from './lti-outcome-url.service';

@Component({
    selector: 'jhi-lti-outcome-url-delete-dialog',
    templateUrl: './lti-outcome-url-delete-dialog.component.html'
})
export class LtiOutcomeUrlDeleteDialogComponent {
    ltiOutcomeUrl: ILtiOutcomeUrl;

    constructor(
        private ltiOutcomeUrlService: LtiOutcomeUrlService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.ltiOutcomeUrlService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'ltiOutcomeUrlListModification',
                content: 'Deleted an ltiOutcomeUrl'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-lti-outcome-url-delete-popup',
    template: ''
})
export class LtiOutcomeUrlDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ ltiOutcomeUrl }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(LtiOutcomeUrlDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.ltiOutcomeUrl = ltiOutcomeUrl;
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
