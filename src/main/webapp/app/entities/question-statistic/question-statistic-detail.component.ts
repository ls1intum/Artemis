import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IQuestionStatistic } from 'app/shared/model/question-statistic.model';

@Component({
    selector: 'jhi-question-statistic-detail',
    templateUrl: './question-statistic-detail.component.html'
})
export class QuestionStatisticDetailComponent implements OnInit {
    questionStatistic: IQuestionStatistic;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ questionStatistic }) => {
            this.questionStatistic = questionStatistic;
        });
    }

    previousState() {
        window.history.back();
    }
}
