import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { DropLocation } from './drop-location.model';
import { DropLocationService } from './drop-location.service';
import { Principal } from '../../shared';

@Component({
    selector: 'jhi-drop-location',
    templateUrl: './drop-location.component.html'
})
export class DropLocationComponent implements OnInit, OnDestroy {
dropLocations: DropLocation[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private dropLocationService: DropLocationService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {
    }

    loadAll() {
        this.dropLocationService.query().subscribe(
            (res: HttpResponse<DropLocation[]>) => {
                this.dropLocations = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }
    ngOnInit() {
        this.loadAll();
        this.principal.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInDropLocations();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: DropLocation) {
        return item.id;
    }
    registerChangeInDropLocations() {
        this.eventSubscriber = this.eventManager.subscribe('dropLocationListModification', (response) => this.loadAll());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
