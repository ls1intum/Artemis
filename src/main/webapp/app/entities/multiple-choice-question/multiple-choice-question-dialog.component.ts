import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { MultipleChoiceQuestion } from './multiple-choice-question.model';
import { MultipleChoiceQuestionPopupService } from './multiple-choice-question-popup.service';
import { MultipleChoiceQuestionService } from './multiple-choice-question.service';

@Component({
    selector: 'jhi-multiple-choice-question-dialog',
    templateUrl: './multiple-choice-question-dialog.component.html'
})
export class MultipleChoiceQuestionDialogComponent implements OnInit {

    multipleChoiceQuestion: MultipleChoiceQuestion;
    isSaving: boolean;

    constructor(
        public activeModal: NgbActiveModal,
        private multipleChoiceQuestionService: MultipleChoiceQuestionService,
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
        if (this.multipleChoiceQuestion.id !== undefined) {
            this.subscribeToSaveResponse(
                this.multipleChoiceQuestionService.update(this.multipleChoiceQuestion));
        } else {
            this.subscribeToSaveResponse(
                this.multipleChoiceQuestionService.create(this.multipleChoiceQuestion));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<MultipleChoiceQuestion>>) {
        result.subscribe((res: HttpResponse<MultipleChoiceQuestion>) =>
            this.onSaveSuccess(res.body), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: MultipleChoiceQuestion) {
        this.eventManager.broadcast({ name: 'multipleChoiceQuestionListModification', content: 'OK'});
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }
}

@Component({
    selector: 'jhi-multiple-choice-question-popup',
    template: ''
})
export class MultipleChoiceQuestionPopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private multipleChoiceQuestionPopupService: MultipleChoiceQuestionPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            if ( params['id'] ) {
                this.multipleChoiceQuestionPopupService
                    .open(MultipleChoiceQuestionDialogComponent as Component, params['id']);
            } else {
                this.multipleChoiceQuestionPopupService
                    .open(MultipleChoiceQuestionDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
