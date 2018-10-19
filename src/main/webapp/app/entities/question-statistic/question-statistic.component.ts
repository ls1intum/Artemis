import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IQuestionStatistic } from 'app/shared/model/question-statistic.model';
import { Principal } from 'app/core';
import { QuestionStatisticService } from './question-statistic.service';

@Component({
    selector: 'jhi-question-statistic',
    templateUrl: './question-statistic.component.html'
})
export class QuestionStatisticComponent implements OnInit, OnDestroy {
    questionStatistics: IQuestionStatistic[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private questionStatisticService: QuestionStatisticService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.questionStatisticService.query().subscribe(
            (res: HttpResponse<IQuestionStatistic[]>) => {
                this.questionStatistics = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInQuestionStatistics();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IQuestionStatistic) {
        return item.id;
    }

    registerChangeInQuestionStatistics() {
        this.eventSubscriber = this.eventManager.subscribe('questionStatisticListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
