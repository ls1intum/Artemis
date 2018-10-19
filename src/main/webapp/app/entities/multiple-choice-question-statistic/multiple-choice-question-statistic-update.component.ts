import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { IMultipleChoiceQuestionStatistic } from 'app/shared/model/multiple-choice-question-statistic.model';
import { MultipleChoiceQuestionStatisticService } from './multiple-choice-question-statistic.service';

@Component({
    selector: 'jhi-multiple-choice-question-statistic-update',
    templateUrl: './multiple-choice-question-statistic-update.component.html'
})
export class MultipleChoiceQuestionStatisticUpdateComponent implements OnInit {
    multipleChoiceQuestionStatistic: IMultipleChoiceQuestionStatistic;
    isSaving: boolean;

    constructor(
        private multipleChoiceQuestionStatisticService: MultipleChoiceQuestionStatisticService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ multipleChoiceQuestionStatistic }) => {
            this.multipleChoiceQuestionStatistic = multipleChoiceQuestionStatistic;
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.multipleChoiceQuestionStatistic.id !== undefined) {
            this.subscribeToSaveResponse(this.multipleChoiceQuestionStatisticService.update(this.multipleChoiceQuestionStatistic));
        } else {
            this.subscribeToSaveResponse(this.multipleChoiceQuestionStatisticService.create(this.multipleChoiceQuestionStatistic));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IMultipleChoiceQuestionStatistic>>) {
        result.subscribe(
            (res: HttpResponse<IMultipleChoiceQuestionStatistic>) => this.onSaveSuccess(),
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
}
