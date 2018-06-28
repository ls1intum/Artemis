import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { LtiUserId } from './lti-user-id.model';
import { LtiUserIdPopupService } from './lti-user-id-popup.service';
import { LtiUserIdService } from './lti-user-id.service';
import { User, UserService } from '../../shared';

@Component({
    selector: 'jhi-lti-user-id-dialog',
    templateUrl: './lti-user-id-dialog.component.html'
})
export class LtiUserIdDialogComponent implements OnInit {

    ltiUserId: LtiUserId;
    isSaving: boolean;

    users: User[];

    constructor(
        public activeModal: NgbActiveModal,
        private jhiAlertService: JhiAlertService,
        private ltiUserIdService: LtiUserIdService,
        private userService: UserService,
        private eventManager: JhiEventManager
    ) {
    }

    ngOnInit() {
        this.isSaving = false;
        this.userService.query()
            .subscribe((res: HttpResponse<User[]>) => { this.users = res.body; }, (res: HttpErrorResponse) => this.onError(res.message));
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.isSaving = true;
        if (this.ltiUserId.id !== undefined) {
            this.subscribeToSaveResponse(
                this.ltiUserIdService.update(this.ltiUserId));
        } else {
            this.subscribeToSaveResponse(
                this.ltiUserIdService.create(this.ltiUserId));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<LtiUserId>>) {
        result.subscribe((res: HttpResponse<LtiUserId>) =>
            this.onSaveSuccess(res.body), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: LtiUserId) {
        this.eventManager.broadcast({ name: 'ltiUserIdListModification', content: 'OK'});
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(error: any) {
        this.jhiAlertService.error(error.message, null, null);
    }

    trackUserById(index: number, item: User) {
        return item.id;
    }
}

@Component({
    selector: 'jhi-lti-user-id-popup',
    template: ''
})
export class LtiUserIdPopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private ltiUserIdPopupService: LtiUserIdPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            if ( params['id'] ) {
                this.ltiUserIdPopupService
                    .open(LtiUserIdDialogComponent as Component, params['id']);
            } else {
                this.ltiUserIdPopupService
                    .open(LtiUserIdDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
