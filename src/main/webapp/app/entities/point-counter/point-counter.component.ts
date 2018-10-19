import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IPointCounter } from 'app/shared/model/point-counter.model';
import { Principal } from 'app/core';
import { PointCounterService } from './point-counter.service';

@Component({
    selector: 'jhi-point-counter',
    templateUrl: './point-counter.component.html'
})
export class PointCounterComponent implements OnInit, OnDestroy {
    pointCounters: IPointCounter[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private pointCounterService: PointCounterService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.pointCounterService.query().subscribe(
            (res: HttpResponse<IPointCounter[]>) => {
                this.pointCounters = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInPointCounters();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IPointCounter) {
        return item.id;
    }

    registerChangeInPointCounters() {
        this.eventSubscriber = this.eventManager.subscribe('pointCounterListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
