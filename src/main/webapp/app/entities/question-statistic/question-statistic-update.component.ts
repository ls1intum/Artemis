import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

import { IQuestionStatistic } from 'app/shared/model/question-statistic.model';
import { QuestionStatisticService } from './question-statistic.service';
import { IQuestion } from 'app/shared/model/question.model';
import { QuestionService } from 'app/entities/question';

@Component({
    selector: 'jhi-question-statistic-update',
    templateUrl: './question-statistic-update.component.html'
})
export class QuestionStatisticUpdateComponent implements OnInit {
    questionStatistic: IQuestionStatistic;
    isSaving: boolean;

    questions: IQuestion[];

    constructor(
        private jhiAlertService: JhiAlertService,
        private questionStatisticService: QuestionStatisticService,
        private questionService: QuestionService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ questionStatistic }) => {
            this.questionStatistic = questionStatistic;
        });
        this.questionService.query().subscribe(
            (res: HttpResponse<IQuestion[]>) => {
                this.questions = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.questionStatistic.id !== undefined) {
            this.subscribeToSaveResponse(this.questionStatisticService.update(this.questionStatistic));
        } else {
            this.subscribeToSaveResponse(this.questionStatisticService.create(this.questionStatistic));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IQuestionStatistic>>) {
        result.subscribe((res: HttpResponse<IQuestionStatistic>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
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

    trackQuestionById(index: number, item: IQuestion) {
        return item.id;
    }
}
