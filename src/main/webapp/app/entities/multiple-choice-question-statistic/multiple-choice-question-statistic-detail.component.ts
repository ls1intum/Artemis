import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IMultipleChoiceQuestionStatistic } from 'app/shared/model/multiple-choice-question-statistic.model';

@Component({
    selector: 'jhi-multiple-choice-question-statistic-detail',
    templateUrl: './multiple-choice-question-statistic-detail.component.html'
})
export class MultipleChoiceQuestionStatisticDetailComponent implements OnInit {
    multipleChoiceQuestionStatistic: IMultipleChoiceQuestionStatistic;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ multipleChoiceQuestionStatistic }) => {
            this.multipleChoiceQuestionStatistic = multipleChoiceQuestionStatistic;
        });
    }

    previousState() {
        window.history.back();
    }
}
