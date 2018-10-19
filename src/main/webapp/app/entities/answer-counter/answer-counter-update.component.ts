import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

import { IAnswerCounter } from 'app/shared/model/answer-counter.model';
import { AnswerCounterService } from './answer-counter.service';
import { IAnswerOption } from 'app/shared/model/answer-option.model';
import { AnswerOptionService } from 'app/entities/answer-option';
import { IMultipleChoiceQuestionStatistic } from 'app/shared/model/multiple-choice-question-statistic.model';
import { MultipleChoiceQuestionStatisticService } from 'app/entities/multiple-choice-question-statistic';

@Component({
    selector: 'jhi-answer-counter-update',
    templateUrl: './answer-counter-update.component.html'
})
export class AnswerCounterUpdateComponent implements OnInit {
    answerCounter: IAnswerCounter;
    isSaving: boolean;

    answers: IAnswerOption[];

    multiplechoicequestionstatistics: IMultipleChoiceQuestionStatistic[];

    constructor(
        private jhiAlertService: JhiAlertService,
        private answerCounterService: AnswerCounterService,
        private answerOptionService: AnswerOptionService,
        private multipleChoiceQuestionStatisticService: MultipleChoiceQuestionStatisticService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ answerCounter }) => {
            this.answerCounter = answerCounter;
        });
        this.answerOptionService.query({ filter: 'answercounter-is-null' }).subscribe(
            (res: HttpResponse<IAnswerOption[]>) => {
                if (!this.answerCounter.answer || !this.answerCounter.answer.id) {
                    this.answers = res.body;
                } else {
                    this.answerOptionService.find(this.answerCounter.answer.id).subscribe(
                        (subRes: HttpResponse<IAnswerOption>) => {
                            this.answers = [subRes.body].concat(res.body);
                        },
                        (subRes: HttpErrorResponse) => this.onError(subRes.message)
                    );
                }
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
        this.multipleChoiceQuestionStatisticService.query().subscribe(
            (res: HttpResponse<IMultipleChoiceQuestionStatistic[]>) => {
                this.multiplechoicequestionstatistics = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.answerCounter.id !== undefined) {
            this.subscribeToSaveResponse(this.answerCounterService.update(this.answerCounter));
        } else {
            this.subscribeToSaveResponse(this.answerCounterService.create(this.answerCounter));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IAnswerCounter>>) {
        result.subscribe((res: HttpResponse<IAnswerCounter>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
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

    trackMultipleChoiceQuestionStatisticById(index: number, item: IMultipleChoiceQuestionStatistic) {
        return item.id;
    }
}
