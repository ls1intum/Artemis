import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { LtiUserId } from './lti-user-id.model';
import { LtiUserIdPopupService } from './lti-user-id-popup.service';
import { LtiUserIdService } from './lti-user-id.service';

@Component({
    selector: 'jhi-lti-user-id-delete-dialog',
    templateUrl: './lti-user-id-delete-dialog.component.html'
})
export class LtiUserIdDeleteDialogComponent {

    ltiUserId: LtiUserId;

    constructor(
        private ltiUserIdService: LtiUserIdService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.ltiUserIdService.delete(id).subscribe((response) => {
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

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private ltiUserIdPopupService: LtiUserIdPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.ltiUserIdPopupService
                .open(LtiUserIdDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
