import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IFeedback } from 'app/shared/model/feedback.model';
import { Principal } from 'app/core';
import { FeedbackService } from './feedback.service';

@Component({
    selector: 'jhi-feedback',
    templateUrl: './feedback.component.html'
})
export class FeedbackComponent implements OnInit, OnDestroy {
    feedbacks: IFeedback[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private feedbackService: FeedbackService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.feedbackService.query().subscribe(
            (res: HttpResponse<IFeedback[]>) => {
                this.feedbacks = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInFeedbacks();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IFeedback) {
        return item.id;
    }

    registerChangeInFeedbacks() {
        this.eventSubscriber = this.eventManager.subscribe('feedbackListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
