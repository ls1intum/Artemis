import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IDropLocationCounter } from 'app/shared/model/drop-location-counter.model';
import { Principal } from 'app/core';
import { DropLocationCounterService } from './drop-location-counter.service';

@Component({
    selector: 'jhi-drop-location-counter',
    templateUrl: './drop-location-counter.component.html'
})
export class DropLocationCounterComponent implements OnInit, OnDestroy {
    dropLocationCounters: IDropLocationCounter[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private dropLocationCounterService: DropLocationCounterService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.dropLocationCounterService.query().subscribe(
            (res: HttpResponse<IDropLocationCounter[]>) => {
                this.dropLocationCounters = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInDropLocationCounters();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IDropLocationCounter) {
        return item.id;
    }

    registerChangeInDropLocationCounters() {
        this.eventSubscriber = this.eventManager.subscribe('dropLocationCounterListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
