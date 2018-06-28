import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { LtiOutcomeUrl } from './lti-outcome-url.model';
import { LtiOutcomeUrlService } from './lti-outcome-url.service';
import { Principal } from '../../shared';

@Component({
    selector: 'jhi-lti-outcome-url',
    templateUrl: './lti-outcome-url.component.html'
})
export class LtiOutcomeUrlComponent implements OnInit, OnDestroy {
ltiOutcomeUrls: LtiOutcomeUrl[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private ltiOutcomeUrlService: LtiOutcomeUrlService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {
    }

    loadAll() {
        this.ltiOutcomeUrlService.query().subscribe(
            (res: HttpResponse<LtiOutcomeUrl[]>) => {
                this.ltiOutcomeUrls = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }
    ngOnInit() {
        this.loadAll();
        this.principal.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInLtiOutcomeUrls();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: LtiOutcomeUrl) {
        return item.id;
    }
    registerChangeInLtiOutcomeUrls() {
        this.eventSubscriber = this.eventManager.subscribe('ltiOutcomeUrlListModification', (response) => this.loadAll());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
