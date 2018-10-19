import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IStatistic } from 'app/shared/model/statistic.model';
import { Principal } from 'app/core';
import { StatisticService } from './statistic.service';

@Component({
    selector: 'jhi-statistic',
    templateUrl: './statistic.component.html'
})
export class StatisticComponent implements OnInit, OnDestroy {
    statistics: IStatistic[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private statisticService: StatisticService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.statisticService.query().subscribe(
            (res: HttpResponse<IStatistic[]>) => {
                this.statistics = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInStatistics();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IStatistic) {
        return item.id;
    }

    registerChangeInStatistics() {
        this.eventSubscriber = this.eventManager.subscribe('statisticListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
