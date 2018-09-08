import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IDropLocation } from 'app/shared/model/drop-location.model';
import { Principal } from 'app/core';
import { DropLocationService } from './drop-location.service';

@Component({
    selector: 'jhi-drop-location',
    templateUrl: './drop-location.component.html'
})
export class DropLocationComponent implements OnInit, OnDestroy {
    dropLocations: IDropLocation[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private dropLocationService: DropLocationService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.dropLocationService.query().subscribe(
            (res: HttpResponse<IDropLocation[]>) => {
                this.dropLocations = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInDropLocations();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IDropLocation) {
        return item.id;
    }

    registerChangeInDropLocations() {
        this.eventSubscriber = this.eventManager.subscribe('dropLocationListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
