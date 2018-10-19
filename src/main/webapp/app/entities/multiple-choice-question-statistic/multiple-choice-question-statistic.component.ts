import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IMultipleChoiceQuestionStatistic } from 'app/shared/model/multiple-choice-question-statistic.model';
import { Principal } from 'app/core';
import { MultipleChoiceQuestionStatisticService } from './multiple-choice-question-statistic.service';

@Component({
    selector: 'jhi-multiple-choice-question-statistic',
    templateUrl: './multiple-choice-question-statistic.component.html'
})
export class MultipleChoiceQuestionStatisticComponent implements OnInit, OnDestroy {
    multipleChoiceQuestionStatistics: IMultipleChoiceQuestionStatistic[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private multipleChoiceQuestionStatisticService: MultipleChoiceQuestionStatisticService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.multipleChoiceQuestionStatisticService.query().subscribe(
            (res: HttpResponse<IMultipleChoiceQuestionStatistic[]>) => {
                this.multipleChoiceQuestionStatistics = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInMultipleChoiceQuestionStatistics();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IMultipleChoiceQuestionStatistic) {
        return item.id;
    }

    registerChangeInMultipleChoiceQuestionStatistics() {
        this.eventSubscriber = this.eventManager.subscribe('multipleChoiceQuestionStatisticListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
