import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { DragAndDropAssignment } from './drag-and-drop-assignment.model';
import { DragAndDropAssignmentService } from './drag-and-drop-assignment.service';

@Component({
    selector: 'jhi-drag-and-drop-assignment-detail',
    templateUrl: './drag-and-drop-assignment-detail.component.html'
})
export class DragAndDropAssignmentDetailComponent implements OnInit, OnDestroy {

    dragAndDropAssignment: DragAndDropAssignment;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private dragAndDropAssignmentService: DragAndDropAssignmentService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['id']);
        });
        this.registerChangeInDragAndDropAssignments();
    }

    load(id) {
        this.dragAndDropAssignmentService.find(id)
            .subscribe((dragAndDropAssignmentResponse: HttpResponse<DragAndDropAssignment>) => {
                this.dragAndDropAssignment = dragAndDropAssignmentResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInDragAndDropAssignments() {
        this.eventSubscriber = this.eventManager.subscribe(
            'dragAndDropAssignmentListModification',
            (response) => this.load(this.dragAndDropAssignment.id)
        );
    }
}
