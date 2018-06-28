import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { DragItem } from './drag-item.model';
import { DragItemPopupService } from './drag-item-popup.service';
import { DragItemService } from './drag-item.service';
import { DropLocation, DropLocationService } from '../drop-location';
import { DragAndDropQuestion, DragAndDropQuestionService } from '../drag-and-drop-question';

@Component({
    selector: 'jhi-drag-item-dialog',
    templateUrl: './drag-item-dialog.component.html'
})
export class DragItemDialogComponent implements OnInit {

    dragItem: DragItem;
    isSaving: boolean;

    correctlocations: DropLocation[];

    draganddropquestions: DragAndDropQuestion[];

    constructor(
        public activeModal: NgbActiveModal,
        private jhiAlertService: JhiAlertService,
        private dragItemService: DragItemService,
        private dropLocationService: DropLocationService,
        private dragAndDropQuestionService: DragAndDropQuestionService,
        private eventManager: JhiEventManager
    ) {
    }

    ngOnInit() {
        this.isSaving = false;
        this.dropLocationService
            .query({filter: 'dragitem-is-null'})
            .subscribe((res: HttpResponse<DropLocation[]>) => {
                if (!this.dragItem.correctLocation || !this.dragItem.correctLocation.id) {
                    this.correctlocations = res.body;
                } else {
                    this.dropLocationService
                        .find(this.dragItem.correctLocation.id)
                        .subscribe((subRes: HttpResponse<DropLocation>) => {
                            this.correctlocations = [subRes.body].concat(res.body);
                        }, (subRes: HttpErrorResponse) => this.onError(subRes.message));
                }
            }, (res: HttpErrorResponse) => this.onError(res.message));
        this.dragAndDropQuestionService.query()
            .subscribe((res: HttpResponse<DragAndDropQuestion[]>) => { this.draganddropquestions = res.body; }, (res: HttpErrorResponse) => this.onError(res.message));
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.isSaving = true;
        if (this.dragItem.id !== undefined) {
            this.subscribeToSaveResponse(
                this.dragItemService.update(this.dragItem));
        } else {
            this.subscribeToSaveResponse(
                this.dragItemService.create(this.dragItem));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<DragItem>>) {
        result.subscribe((res: HttpResponse<DragItem>) =>
            this.onSaveSuccess(res.body), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: DragItem) {
        this.eventManager.broadcast({ name: 'dragItemListModification', content: 'OK'});
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(error: any) {
        this.jhiAlertService.error(error.message, null, null);
    }

    trackDropLocationById(index: number, item: DropLocation) {
        return item.id;
    }

    trackDragAndDropQuestionById(index: number, item: DragAndDropQuestion) {
        return item.id;
    }
}

@Component({
    selector: 'jhi-drag-item-popup',
    template: ''
})
export class DragItemPopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private dragItemPopupService: DragItemPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            if ( params['id'] ) {
                this.dragItemPopupService
                    .open(DragItemDialogComponent as Component, params['id']);
            } else {
                this.dragItemPopupService
                    .open(DragItemDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
