import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { LtiUserId } from './lti-user-id.model';
import { LtiUserIdService } from './lti-user-id.service';

@Component({
    selector: 'jhi-lti-user-id-detail',
    templateUrl: './lti-user-id-detail.component.html'
})
export class LtiUserIdDetailComponent implements OnInit, OnDestroy {

    ltiUserId: LtiUserId;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private ltiUserIdService: LtiUserIdService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['id']);
        });
        this.registerChangeInLtiUserIds();
    }

    load(id) {
        this.ltiUserIdService.find(id)
            .subscribe((ltiUserIdResponse: HttpResponse<LtiUserId>) => {
                this.ltiUserId = ltiUserIdResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInLtiUserIds() {
        this.eventSubscriber = this.eventManager.subscribe(
            'ltiUserIdListModification',
            (response) => this.load(this.ltiUserId.id)
        );
    }
}
