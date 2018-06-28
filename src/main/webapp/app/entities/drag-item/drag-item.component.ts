import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { DragItem } from './drag-item.model';
import { DragItemService } from './drag-item.service';
import { Principal } from '../../shared';

@Component({
    selector: 'jhi-drag-item',
    templateUrl: './drag-item.component.html'
})
export class DragItemComponent implements OnInit, OnDestroy {
dragItems: DragItem[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private dragItemService: DragItemService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {
    }

    loadAll() {
        this.dragItemService.query().subscribe(
            (res: HttpResponse<DragItem[]>) => {
                this.dragItems = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }
    ngOnInit() {
        this.loadAll();
        this.principal.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInDragItems();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: DragItem) {
        return item.id;
    }
    registerChangeInDragItems() {
        this.eventSubscriber = this.eventManager.subscribe('dragItemListModification', (response) => this.loadAll());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
