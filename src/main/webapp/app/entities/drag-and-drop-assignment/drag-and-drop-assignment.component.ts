import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { DragAndDropAssignment } from './drag-and-drop-assignment.model';
import { DragAndDropAssignmentService } from './drag-and-drop-assignment.service';
import { Principal } from '../../shared';

@Component({
    selector: 'jhi-drag-and-drop-assignment',
    templateUrl: './drag-and-drop-assignment.component.html'
})
export class DragAndDropAssignmentComponent implements OnInit, OnDestroy {
dragAndDropAssignments: DragAndDropAssignment[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private dragAndDropAssignmentService: DragAndDropAssignmentService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {
    }

    loadAll() {
        this.dragAndDropAssignmentService.query().subscribe(
            (res: HttpResponse<DragAndDropAssignment[]>) => {
                this.dragAndDropAssignments = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }
    ngOnInit() {
        this.loadAll();
        this.principal.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInDragAndDropAssignments();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: DragAndDropAssignment) {
        return item.id;
    }
    registerChangeInDragAndDropAssignments() {
        this.eventSubscriber = this.eventManager.subscribe('dragAndDropAssignmentListModification', (response) => this.loadAll());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
