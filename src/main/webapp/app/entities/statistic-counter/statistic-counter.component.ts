import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IStatisticCounter } from 'app/shared/model/statistic-counter.model';
import { Principal } from 'app/core';
import { StatisticCounterService } from './statistic-counter.service';

@Component({
    selector: 'jhi-statistic-counter',
    templateUrl: './statistic-counter.component.html'
})
export class StatisticCounterComponent implements OnInit, OnDestroy {
    statisticCounters: IStatisticCounter[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private statisticCounterService: StatisticCounterService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.statisticCounterService.query().subscribe(
            (res: HttpResponse<IStatisticCounter[]>) => {
                this.statisticCounters = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInStatisticCounters();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IStatisticCounter) {
        return item.id;
    }

    registerChangeInStatisticCounters() {
        this.eventSubscriber = this.eventManager.subscribe('statisticCounterListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
