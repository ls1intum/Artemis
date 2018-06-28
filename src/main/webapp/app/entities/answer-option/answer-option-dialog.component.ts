import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { AnswerOption } from './answer-option.model';
import { AnswerOptionPopupService } from './answer-option-popup.service';
import { AnswerOptionService } from './answer-option.service';
import { MultipleChoiceQuestion, MultipleChoiceQuestionService } from '../multiple-choice-question';

@Component({
    selector: 'jhi-answer-option-dialog',
    templateUrl: './answer-option-dialog.component.html'
})
export class AnswerOptionDialogComponent implements OnInit {

    answerOption: AnswerOption;
    isSaving: boolean;

    multiplechoicequestions: MultipleChoiceQuestion[];

    constructor(
        public activeModal: NgbActiveModal,
        private jhiAlertService: JhiAlertService,
        private answerOptionService: AnswerOptionService,
        private multipleChoiceQuestionService: MultipleChoiceQuestionService,
        private eventManager: JhiEventManager
    ) {
    }

    ngOnInit() {
        this.isSaving = false;
        this.multipleChoiceQuestionService.query()
            .subscribe((res: HttpResponse<MultipleChoiceQuestion[]>) => { this.multiplechoicequestions = res.body; }, (res: HttpErrorResponse) => this.onError(res.message));
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.isSaving = true;
        if (this.answerOption.id !== undefined) {
            this.subscribeToSaveResponse(
                this.answerOptionService.update(this.answerOption));
        } else {
            this.subscribeToSaveResponse(
                this.answerOptionService.create(this.answerOption));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<AnswerOption>>) {
        result.subscribe((res: HttpResponse<AnswerOption>) =>
            this.onSaveSuccess(res.body), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: AnswerOption) {
        this.eventManager.broadcast({ name: 'answerOptionListModification', content: 'OK'});
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(error: any) {
        this.jhiAlertService.error(error.message, null, null);
    }

    trackMultipleChoiceQuestionById(index: number, item: MultipleChoiceQuestion) {
        return item.id;
    }
}

@Component({
    selector: 'jhi-answer-option-popup',
    template: ''
})
export class AnswerOptionPopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private answerOptionPopupService: AnswerOptionPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            if ( params['id'] ) {
                this.answerOptionPopupService
                    .open(AnswerOptionDialogComponent as Component, params['id']);
            } else {
                this.answerOptionPopupService
                    .open(AnswerOptionDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
