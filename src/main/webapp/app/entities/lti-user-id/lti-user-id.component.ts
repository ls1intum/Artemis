import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { ILtiUserId } from 'app/shared/model/lti-user-id.model';
import { Principal } from 'app/core';
import { LtiUserIdService } from './lti-user-id.service';

@Component({
    selector: 'jhi-lti-user-id',
    templateUrl: './lti-user-id.component.html'
})
export class LtiUserIdComponent implements OnInit, OnDestroy {
    ltiUserIds: ILtiUserId[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private ltiUserIdService: LtiUserIdService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.ltiUserIdService.query().subscribe(
            (res: HttpResponse<ILtiUserId[]>) => {
                this.ltiUserIds = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInLtiUserIds();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: ILtiUserId) {
        return item.id;
    }

    registerChangeInLtiUserIds() {
        this.eventSubscriber = this.eventManager.subscribe('ltiUserIdListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
