import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IQuizPointStatistic } from 'app/shared/model/quiz-point-statistic.model';

@Component({
    selector: 'jhi-quiz-point-statistic-detail',
    templateUrl: './quiz-point-statistic-detail.component.html'
})
export class QuizPointStatisticDetailComponent implements OnInit {
    quizPointStatistic: IQuizPointStatistic;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ quizPointStatistic }) => {
            this.quizPointStatistic = quizPointStatistic;
        });
    }

    previousState() {
        window.history.back();
    }
}
