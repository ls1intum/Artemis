import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IDragAndDropAssignment } from 'app/shared/model/drag-and-drop-assignment.model';
import { Principal } from 'app/core';
import { DragAndDropAssignmentService } from './drag-and-drop-assignment.service';

@Component({
    selector: 'jhi-drag-and-drop-assignment',
    templateUrl: './drag-and-drop-assignment.component.html'
})
export class DragAndDropAssignmentComponent implements OnInit, OnDestroy {
    dragAndDropAssignments: IDragAndDropAssignment[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private dragAndDropAssignmentService: DragAndDropAssignmentService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.dragAndDropAssignmentService.query().subscribe(
            (res: HttpResponse<IDragAndDropAssignment[]>) => {
                this.dragAndDropAssignments = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInDragAndDropAssignments();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IDragAndDropAssignment) {
        return item.id;
    }

    registerChangeInDragAndDropAssignments() {
        this.eventSubscriber = this.eventManager.subscribe('dragAndDropAssignmentListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
