import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { LtiOutcomeUrl } from './lti-outcome-url.model';
import { LtiOutcomeUrlService } from './lti-outcome-url.service';

@Component({
    selector: 'jhi-lti-outcome-url-detail',
    templateUrl: './lti-outcome-url-detail.component.html'
})
export class LtiOutcomeUrlDetailComponent implements OnInit, OnDestroy {

    ltiOutcomeUrl: LtiOutcomeUrl;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private ltiOutcomeUrlService: LtiOutcomeUrlService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['id']);
        });
        this.registerChangeInLtiOutcomeUrls();
    }

    load(id) {
        this.ltiOutcomeUrlService.find(id)
            .subscribe((ltiOutcomeUrlResponse: HttpResponse<LtiOutcomeUrl>) => {
                this.ltiOutcomeUrl = ltiOutcomeUrlResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInLtiOutcomeUrls() {
        this.eventSubscriber = this.eventManager.subscribe(
            'ltiOutcomeUrlListModification',
            (response) => this.load(this.ltiOutcomeUrl.id)
        );
    }
}
