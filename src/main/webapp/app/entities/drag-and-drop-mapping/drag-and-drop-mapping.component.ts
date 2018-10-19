import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IDragAndDropMapping } from 'app/shared/model/drag-and-drop-mapping.model';
import { Principal } from 'app/core';
import { DragAndDropMappingService } from './drag-and-drop-mapping.service';

@Component({
    selector: 'jhi-drag-and-drop-mapping',
    templateUrl: './drag-and-drop-mapping.component.html'
})
export class DragAndDropMappingComponent implements OnInit, OnDestroy {
    dragAndDropMappings: IDragAndDropMapping[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private dragAndDropMappingService: DragAndDropMappingService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.dragAndDropMappingService.query().subscribe(
            (res: HttpResponse<IDragAndDropMapping[]>) => {
                this.dragAndDropMappings = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInDragAndDropMappings();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IDragAndDropMapping) {
        return item.id;
    }

    registerChangeInDragAndDropMappings() {
        this.eventSubscriber = this.eventManager.subscribe('dragAndDropMappingListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
