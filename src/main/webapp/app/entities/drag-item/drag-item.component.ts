import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IDragItem } from 'app/shared/model/drag-item.model';
import { Principal } from 'app/core';
import { DragItemService } from './drag-item.service';

@Component({
    selector: 'jhi-drag-item',
    templateUrl: './drag-item.component.html'
})
export class DragItemComponent implements OnInit, OnDestroy {
    dragItems: IDragItem[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private dragItemService: DragItemService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.dragItemService.query().subscribe(
            (res: HttpResponse<IDragItem[]>) => {
                this.dragItems = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInDragItems();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IDragItem) {
        return item.id;
    }

    registerChangeInDragItems() {
        this.eventSubscriber = this.eventManager.subscribe('dragItemListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
