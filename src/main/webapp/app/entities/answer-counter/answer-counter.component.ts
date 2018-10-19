import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IAnswerCounter } from 'app/shared/model/answer-counter.model';
import { Principal } from 'app/core';
import { AnswerCounterService } from './answer-counter.service';

@Component({
    selector: 'jhi-answer-counter',
    templateUrl: './answer-counter.component.html'
})
export class AnswerCounterComponent implements OnInit, OnDestroy {
    answerCounters: IAnswerCounter[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private answerCounterService: AnswerCounterService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.answerCounterService.query().subscribe(
            (res: HttpResponse<IAnswerCounter[]>) => {
                this.answerCounters = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInAnswerCounters();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IAnswerCounter) {
        return item.id;
    }

    registerChangeInAnswerCounters() {
        this.eventSubscriber = this.eventManager.subscribe('answerCounterListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
