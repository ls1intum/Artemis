import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { DropLocation } from './drop-location.model';
import { DropLocationPopupService } from './drop-location-popup.service';
import { DropLocationService } from './drop-location.service';
import { DragAndDropQuestion, DragAndDropQuestionService } from '../drag-and-drop-question';

@Component({
    selector: 'jhi-drop-location-dialog',
    templateUrl: './drop-location-dialog.component.html'
})
export class DropLocationDialogComponent implements OnInit {

    dropLocation: DropLocation;
    isSaving: boolean;

    draganddropquestions: DragAndDropQuestion[];

    constructor(
        public activeModal: NgbActiveModal,
        private jhiAlertService: JhiAlertService,
        private dropLocationService: DropLocationService,
        private dragAndDropQuestionService: DragAndDropQuestionService,
        private eventManager: JhiEventManager
    ) {
    }

    ngOnInit() {
        this.isSaving = false;
        this.dragAndDropQuestionService.query()
            .subscribe((res: HttpResponse<DragAndDropQuestion[]>) => { this.draganddropquestions = res.body; }, (res: HttpErrorResponse) => this.onError(res.message));
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.isSaving = true;
        if (this.dropLocation.id !== undefined) {
            this.subscribeToSaveResponse(
                this.dropLocationService.update(this.dropLocation));
        } else {
            this.subscribeToSaveResponse(
                this.dropLocationService.create(this.dropLocation));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<DropLocation>>) {
        result.subscribe((res: HttpResponse<DropLocation>) =>
            this.onSaveSuccess(res.body), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: DropLocation) {
        this.eventManager.broadcast({ name: 'dropLocationListModification', content: 'OK'});
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(error: any) {
        this.jhiAlertService.error(error.message, null, null);
    }

    trackDragAndDropQuestionById(index: number, item: DragAndDropQuestion) {
        return item.id;
    }
}

@Component({
    selector: 'jhi-drop-location-popup',
    template: ''
})
export class DropLocationPopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private dropLocationPopupService: DropLocationPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            if ( params['id'] ) {
                this.dropLocationPopupService
                    .open(DropLocationDialogComponent as Component, params['id']);
            } else {
                this.dropLocationPopupService
                    .open(DropLocationDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
