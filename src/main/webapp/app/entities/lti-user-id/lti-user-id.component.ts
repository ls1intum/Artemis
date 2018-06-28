import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { LtiUserId } from './lti-user-id.model';
import { LtiUserIdService } from './lti-user-id.service';
import { Principal } from '../../shared';

@Component({
    selector: 'jhi-lti-user-id',
    templateUrl: './lti-user-id.component.html'
})
export class LtiUserIdComponent implements OnInit, OnDestroy {
ltiUserIds: LtiUserId[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private ltiUserIdService: LtiUserIdService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {
    }

    loadAll() {
        this.ltiUserIdService.query().subscribe(
            (res: HttpResponse<LtiUserId[]>) => {
                this.ltiUserIds = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }
    ngOnInit() {
        this.loadAll();
        this.principal.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInLtiUserIds();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: LtiUserId) {
        return item.id;
    }
    registerChangeInLtiUserIds() {
        this.eventSubscriber = this.eventManager.subscribe('ltiUserIdListModification', (response) => this.loadAll());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
