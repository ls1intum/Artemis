import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IResult } from 'app/shared/model/result.model';
import { Principal } from 'app/core';
import { ResultService } from './result.service';

@Component({
    selector: 'jhi-result',
    templateUrl: './result.component.html'
})
export class ResultComponent implements OnInit, OnDestroy {
    results: IResult[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private resultService: ResultService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.resultService.query().subscribe(
            (res: HttpResponse<IResult[]>) => {
                this.results = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInResults();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IResult) {
        return item.id;
    }

    registerChangeInResults() {
        this.eventSubscriber = this.eventManager.subscribe('resultListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
