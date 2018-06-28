import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { DragItem } from './drag-item.model';
import { DragItemService } from './drag-item.service';

@Component({
    selector: 'jhi-drag-item-detail',
    templateUrl: './drag-item-detail.component.html'
})
export class DragItemDetailComponent implements OnInit, OnDestroy {

    dragItem: DragItem;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private dragItemService: DragItemService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['id']);
        });
        this.registerChangeInDragItems();
    }

    load(id) {
        this.dragItemService.find(id)
            .subscribe((dragItemResponse: HttpResponse<DragItem>) => {
                this.dragItem = dragItemResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInDragItems() {
        this.eventSubscriber = this.eventManager.subscribe(
            'dragItemListModification',
            (response) => this.load(this.dragItem.id)
        );
    }
}
