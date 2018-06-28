import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { DragAndDropAssignment } from './drag-and-drop-assignment.model';
import { DragAndDropAssignmentPopupService } from './drag-and-drop-assignment-popup.service';
import { DragAndDropAssignmentService } from './drag-and-drop-assignment.service';
import { DragItem, DragItemService } from '../drag-item';
import { DropLocation, DropLocationService } from '../drop-location';
import { DragAndDropSubmittedAnswer, DragAndDropSubmittedAnswerService } from '../drag-and-drop-submitted-answer';

@Component({
    selector: 'jhi-drag-and-drop-assignment-dialog',
    templateUrl: './drag-and-drop-assignment-dialog.component.html'
})
export class DragAndDropAssignmentDialogComponent implements OnInit {

    dragAndDropAssignment: DragAndDropAssignment;
    isSaving: boolean;

    dragitems: DragItem[];

    droplocations: DropLocation[];

    draganddropsubmittedanswers: DragAndDropSubmittedAnswer[];

    constructor(
        public activeModal: NgbActiveModal,
        private jhiAlertService: JhiAlertService,
        private dragAndDropAssignmentService: DragAndDropAssignmentService,
        private dragItemService: DragItemService,
        private dropLocationService: DropLocationService,
        private dragAndDropSubmittedAnswerService: DragAndDropSubmittedAnswerService,
        private eventManager: JhiEventManager
    ) {
    }

    ngOnInit() {
        this.isSaving = false;
        this.dragItemService.query()
            .subscribe((res: HttpResponse<DragItem[]>) => { this.dragitems = res.body; }, (res: HttpErrorResponse) => this.onError(res.message));
        this.dropLocationService.query()
            .subscribe((res: HttpResponse<DropLocation[]>) => { this.droplocations = res.body; }, (res: HttpErrorResponse) => this.onError(res.message));
        this.dragAndDropSubmittedAnswerService.query()
            .subscribe((res: HttpResponse<DragAndDropSubmittedAnswer[]>) => { this.draganddropsubmittedanswers = res.body; }, (res: HttpErrorResponse) => this.onError(res.message));
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.isSaving = true;
        if (this.dragAndDropAssignment.id !== undefined) {
            this.subscribeToSaveResponse(
                this.dragAndDropAssignmentService.update(this.dragAndDropAssignment));
        } else {
            this.subscribeToSaveResponse(
                this.dragAndDropAssignmentService.create(this.dragAndDropAssignment));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<DragAndDropAssignment>>) {
        result.subscribe((res: HttpResponse<DragAndDropAssignment>) =>
            this.onSaveSuccess(res.body), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: DragAndDropAssignment) {
        this.eventManager.broadcast({ name: 'dragAndDropAssignmentListModification', content: 'OK'});
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(error: any) {
        this.jhiAlertService.error(error.message, null, null);
    }

    trackDragItemById(index: number, item: DragItem) {
        return item.id;
    }

    trackDropLocationById(index: number, item: DropLocation) {
        return item.id;
    }

    trackDragAndDropSubmittedAnswerById(index: number, item: DragAndDropSubmittedAnswer) {
        return item.id;
    }
}

@Component({
    selector: 'jhi-drag-and-drop-assignment-popup',
    template: ''
})
export class DragAndDropAssignmentPopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private dragAndDropAssignmentPopupService: DragAndDropAssignmentPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            if ( params['id'] ) {
                this.dragAndDropAssignmentPopupService
                    .open(DragAndDropAssignmentDialogComponent as Component, params['id']);
            } else {
                this.dragAndDropAssignmentPopupService
                    .open(DragAndDropAssignmentDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
