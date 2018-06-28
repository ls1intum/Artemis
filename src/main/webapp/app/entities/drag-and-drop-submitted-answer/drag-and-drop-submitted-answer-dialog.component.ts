import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { DragAndDropSubmittedAnswer } from './drag-and-drop-submitted-answer.model';
import { DragAndDropSubmittedAnswerPopupService } from './drag-and-drop-submitted-answer-popup.service';
import { DragAndDropSubmittedAnswerService } from './drag-and-drop-submitted-answer.service';

@Component({
    selector: 'jhi-drag-and-drop-submitted-answer-dialog',
    templateUrl: './drag-and-drop-submitted-answer-dialog.component.html'
})
export class DragAndDropSubmittedAnswerDialogComponent implements OnInit {

    dragAndDropSubmittedAnswer: DragAndDropSubmittedAnswer;
    isSaving: boolean;

    constructor(
        public activeModal: NgbActiveModal,
        private dragAndDropSubmittedAnswerService: DragAndDropSubmittedAnswerService,
        private eventManager: JhiEventManager
    ) {
    }

    ngOnInit() {
        this.isSaving = false;
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.isSaving = true;
        if (this.dragAndDropSubmittedAnswer.id !== undefined) {
            this.subscribeToSaveResponse(
                this.dragAndDropSubmittedAnswerService.update(this.dragAndDropSubmittedAnswer));
        } else {
            this.subscribeToSaveResponse(
                this.dragAndDropSubmittedAnswerService.create(this.dragAndDropSubmittedAnswer));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<DragAndDropSubmittedAnswer>>) {
        result.subscribe((res: HttpResponse<DragAndDropSubmittedAnswer>) =>
            this.onSaveSuccess(res.body), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: DragAndDropSubmittedAnswer) {
        this.eventManager.broadcast({ name: 'dragAndDropSubmittedAnswerListModification', content: 'OK'});
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }
}

@Component({
    selector: 'jhi-drag-and-drop-submitted-answer-popup',
    template: ''
})
export class DragAndDropSubmittedAnswerPopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private dragAndDropSubmittedAnswerPopupService: DragAndDropSubmittedAnswerPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            if ( params['id'] ) {
                this.dragAndDropSubmittedAnswerPopupService
                    .open(DragAndDropSubmittedAnswerDialogComponent as Component, params['id']);
            } else {
                this.dragAndDropSubmittedAnswerPopupService
                    .open(DragAndDropSubmittedAnswerDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
