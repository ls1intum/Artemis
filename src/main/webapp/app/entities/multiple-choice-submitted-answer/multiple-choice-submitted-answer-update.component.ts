import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

import { IMultipleChoiceSubmittedAnswer } from 'app/shared/model/multiple-choice-submitted-answer.model';
import { MultipleChoiceSubmittedAnswerService } from './multiple-choice-submitted-answer.service';
import { IAnswerOption } from 'app/shared/model/answer-option.model';
import { AnswerOptionService } from 'app/entities/answer-option';

@Component({
    selector: 'jhi-multiple-choice-submitted-answer-update',
    templateUrl: './multiple-choice-submitted-answer-update.component.html'
})
export class MultipleChoiceSubmittedAnswerUpdateComponent implements OnInit {
    private _multipleChoiceSubmittedAnswer: IMultipleChoiceSubmittedAnswer;
    isSaving: boolean;

    answeroptions: IAnswerOption[];

    constructor(
        private jhiAlertService: JhiAlertService,
        private multipleChoiceSubmittedAnswerService: MultipleChoiceSubmittedAnswerService,
        private answerOptionService: AnswerOptionService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ multipleChoiceSubmittedAnswer }) => {
            this.multipleChoiceSubmittedAnswer = multipleChoiceSubmittedAnswer;
        });
        this.answerOptionService.query().subscribe(
            (res: HttpResponse<IAnswerOption[]>) => {
                this.answeroptions = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.multipleChoiceSubmittedAnswer.id !== undefined) {
            this.subscribeToSaveResponse(this.multipleChoiceSubmittedAnswerService.update(this.multipleChoiceSubmittedAnswer));
        } else {
            this.subscribeToSaveResponse(this.multipleChoiceSubmittedAnswerService.create(this.multipleChoiceSubmittedAnswer));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IMultipleChoiceSubmittedAnswer>>) {
        result.subscribe(
            (res: HttpResponse<IMultipleChoiceSubmittedAnswer>) => this.onSaveSuccess(),
            (res: HttpErrorResponse) => this.onSaveError()
        );
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }

    trackAnswerOptionById(index: number, item: IAnswerOption) {
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
    get multipleChoiceSubmittedAnswer() {
        return this._multipleChoiceSubmittedAnswer;
    }

    set multipleChoiceSubmittedAnswer(multipleChoiceSubmittedAnswer: IMultipleChoiceSubmittedAnswer) {
        this._multipleChoiceSubmittedAnswer = multipleChoiceSubmittedAnswer;
    }
}
