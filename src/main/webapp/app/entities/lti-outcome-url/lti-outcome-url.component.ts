import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { ILtiOutcomeUrl } from 'app/shared/model/lti-outcome-url.model';
import { Principal } from 'app/core';
import { LtiOutcomeUrlService } from './lti-outcome-url.service';

@Component({
    selector: 'jhi-lti-outcome-url',
    templateUrl: './lti-outcome-url.component.html'
})
export class LtiOutcomeUrlComponent implements OnInit, OnDestroy {
    ltiOutcomeUrls: ILtiOutcomeUrl[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private ltiOutcomeUrlService: LtiOutcomeUrlService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.ltiOutcomeUrlService.query().subscribe(
            (res: HttpResponse<ILtiOutcomeUrl[]>) => {
                this.ltiOutcomeUrls = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInLtiOutcomeUrls();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: ILtiOutcomeUrl) {
        return item.id;
    }

    registerChangeInLtiOutcomeUrls() {
        this.eventSubscriber = this.eventManager.subscribe('ltiOutcomeUrlListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
