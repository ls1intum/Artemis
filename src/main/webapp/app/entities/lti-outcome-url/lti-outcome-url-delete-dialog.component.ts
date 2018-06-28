import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { LtiOutcomeUrl } from './lti-outcome-url.model';
import { LtiOutcomeUrlPopupService } from './lti-outcome-url-popup.service';
import { LtiOutcomeUrlService } from './lti-outcome-url.service';

@Component({
    selector: 'jhi-lti-outcome-url-delete-dialog',
    templateUrl: './lti-outcome-url-delete-dialog.component.html'
})
export class LtiOutcomeUrlDeleteDialogComponent {

    ltiOutcomeUrl: LtiOutcomeUrl;

    constructor(
        private ltiOutcomeUrlService: LtiOutcomeUrlService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.ltiOutcomeUrlService.delete(id).subscribe((response) => {
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

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private ltiOutcomeUrlPopupService: LtiOutcomeUrlPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.ltiOutcomeUrlPopupService
                .open(LtiOutcomeUrlDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
