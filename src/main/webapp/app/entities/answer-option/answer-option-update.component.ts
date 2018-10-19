import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

import { IAnswerOption } from 'app/shared/model/answer-option.model';
import { AnswerOptionService } from './answer-option.service';
import { IMultipleChoiceQuestion } from 'app/shared/model/multiple-choice-question.model';
import { MultipleChoiceQuestionService } from 'app/entities/multiple-choice-question';

@Component({
    selector: 'jhi-answer-option-update',
    templateUrl: './answer-option-update.component.html'
})
export class AnswerOptionUpdateComponent implements OnInit {
    answerOption: IAnswerOption;
    isSaving: boolean;

    multiplechoicequestions: IMultipleChoiceQuestion[];

    constructor(
        private jhiAlertService: JhiAlertService,
        private answerOptionService: AnswerOptionService,
        private multipleChoiceQuestionService: MultipleChoiceQuestionService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ answerOption }) => {
            this.answerOption = answerOption;
        });
        this.multipleChoiceQuestionService.query().subscribe(
            (res: HttpResponse<IMultipleChoiceQuestion[]>) => {
                this.multiplechoicequestions = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.answerOption.id !== undefined) {
            this.subscribeToSaveResponse(this.answerOptionService.update(this.answerOption));
        } else {
            this.subscribeToSaveResponse(this.answerOptionService.create(this.answerOption));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IAnswerOption>>) {
        result.subscribe((res: HttpResponse<IAnswerOption>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
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

    trackMultipleChoiceQuestionById(index: number, item: IMultipleChoiceQuestion) {
        return item.id;
    }
}
