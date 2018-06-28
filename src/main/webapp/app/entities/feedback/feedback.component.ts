import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { Feedback } from './feedback.model';
import { FeedbackService } from './feedback.service';
import { Principal } from '../../shared';

@Component({
    selector: 'jhi-feedback',
    templateUrl: './feedback.component.html'
})
export class FeedbackComponent implements OnInit, OnDestroy {
feedbacks: Feedback[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private feedbackService: FeedbackService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {
    }

    loadAll() {
        this.feedbackService.query().subscribe(
            (res: HttpResponse<Feedback[]>) => {
                this.feedbacks = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }
    ngOnInit() {
        this.loadAll();
        this.principal.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInFeedbacks();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: Feedback) {
        return item.id;
    }
    registerChangeInFeedbacks() {
        this.eventSubscriber = this.eventManager.subscribe('feedbackListModification', (response) => this.loadAll());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
