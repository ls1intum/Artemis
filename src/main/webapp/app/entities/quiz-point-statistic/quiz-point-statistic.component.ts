import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IQuizPointStatistic } from 'app/shared/model/quiz-point-statistic.model';
import { Principal } from 'app/core';
import { QuizPointStatisticService } from './quiz-point-statistic.service';

@Component({
    selector: 'jhi-quiz-point-statistic',
    templateUrl: './quiz-point-statistic.component.html'
})
export class QuizPointStatisticComponent implements OnInit, OnDestroy {
    quizPointStatistics: IQuizPointStatistic[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private quizPointStatisticService: QuizPointStatisticService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.quizPointStatisticService.query().subscribe(
            (res: HttpResponse<IQuizPointStatistic[]>) => {
                this.quizPointStatistics = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInQuizPointStatistics();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IQuizPointStatistic) {
        return item.id;
    }

    registerChangeInQuizPointStatistics() {
        this.eventSubscriber = this.eventManager.subscribe('quizPointStatisticListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
