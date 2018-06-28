import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { MultipleChoiceSubmittedAnswer } from './multiple-choice-submitted-answer.model';
import { MultipleChoiceSubmittedAnswerPopupService } from './multiple-choice-submitted-answer-popup.service';
import { MultipleChoiceSubmittedAnswerService } from './multiple-choice-submitted-answer.service';
import { AnswerOption, AnswerOptionService } from '../answer-option';

@Component({
    selector: 'jhi-multiple-choice-submitted-answer-dialog',
    templateUrl: './multiple-choice-submitted-answer-dialog.component.html'
})
export class MultipleChoiceSubmittedAnswerDialogComponent implements OnInit {

    multipleChoiceSubmittedAnswer: MultipleChoiceSubmittedAnswer;
    isSaving: boolean;

    answeroptions: AnswerOption[];

    constructor(
        public activeModal: NgbActiveModal,
        private jhiAlertService: JhiAlertService,
        private multipleChoiceSubmittedAnswerService: MultipleChoiceSubmittedAnswerService,
        private answerOptionService: AnswerOptionService,
        private eventManager: JhiEventManager
    ) {
    }

    ngOnInit() {
        this.isSaving = false;
        this.answerOptionService.query()
            .subscribe((res: HttpResponse<AnswerOption[]>) => { this.answeroptions = res.body; }, (res: HttpErrorResponse) => this.onError(res.message));
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.isSaving = true;
        if (this.multipleChoiceSubmittedAnswer.id !== undefined) {
            this.subscribeToSaveResponse(
                this.multipleChoiceSubmittedAnswerService.update(this.multipleChoiceSubmittedAnswer));
        } else {
            this.subscribeToSaveResponse(
                this.multipleChoiceSubmittedAnswerService.create(this.multipleChoiceSubmittedAnswer));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<MultipleChoiceSubmittedAnswer>>) {
        result.subscribe((res: HttpResponse<MultipleChoiceSubmittedAnswer>) =>
            this.onSaveSuccess(res.body), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: MultipleChoiceSubmittedAnswer) {
        this.eventManager.broadcast({ name: 'multipleChoiceSubmittedAnswerListModification', content: 'OK'});
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(error: any) {
        this.jhiAlertService.error(error.message, null, null);
    }

    trackAnswerOptionById(index: number, item: AnswerOption) {
        return item.id;
    }

    getSelected(selectedVals: Array<any>, option: any) {
        if (selectedVals) {
            for (let i = 0; i < selectedVals.length; i++) {
                if (option.id === selectedVals[i].id) {
                    return selectedVals[i];
                }
            }
        }
        return option;
    }
}

@Component({
    selector: 'jhi-multiple-choice-submitted-answer-popup',
    template: ''
})
export class MultipleChoiceSubmittedAnswerPopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private multipleChoiceSubmittedAnswerPopupService: MultipleChoiceSubmittedAnswerPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            if ( params['id'] ) {
                this.multipleChoiceSubmittedAnswerPopupService
                    .open(MultipleChoiceSubmittedAnswerDialogComponent as Component, params['id']);
            } else {
                this.multipleChoiceSubmittedAnswerPopupService
                    .open(MultipleChoiceSubmittedAnswerDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
