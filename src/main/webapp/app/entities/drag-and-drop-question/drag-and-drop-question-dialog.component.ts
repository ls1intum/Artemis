import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { DragAndDropQuestion } from './drag-and-drop-question.model';
import { DragAndDropQuestionPopupService } from './drag-and-drop-question-popup.service';
import { DragAndDropQuestionService } from './drag-and-drop-question.service';

@Component({
    selector: 'jhi-drag-and-drop-question-dialog',
    templateUrl: './drag-and-drop-question-dialog.component.html'
})
export class DragAndDropQuestionDialogComponent implements OnInit {

    dragAndDropQuestion: DragAndDropQuestion;
    isSaving: boolean;

    constructor(
        public activeModal: NgbActiveModal,
        private dragAndDropQuestionService: DragAndDropQuestionService,
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
        if (this.dragAndDropQuestion.id !== undefined) {
            this.subscribeToSaveResponse(
                this.dragAndDropQuestionService.update(this.dragAndDropQuestion));
        } else {
            this.subscribeToSaveResponse(
                this.dragAndDropQuestionService.create(this.dragAndDropQuestion));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<DragAndDropQuestion>>) {
        result.subscribe((res: HttpResponse<DragAndDropQuestion>) =>
            this.onSaveSuccess(res.body), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: DragAndDropQuestion) {
        this.eventManager.broadcast({ name: 'dragAndDropQuestionListModification', content: 'OK'});
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }
}

@Component({
    selector: 'jhi-drag-and-drop-question-popup',
    template: ''
})
export class DragAndDropQuestionPopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private dragAndDropQuestionPopupService: DragAndDropQuestionPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            if ( params['id'] ) {
                this.dragAndDropQuestionPopupService
                    .open(DragAndDropQuestionDialogComponent as Component, params['id']);
            } else {
                this.dragAndDropQuestionPopupService
                    .open(DragAndDropQuestionDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
