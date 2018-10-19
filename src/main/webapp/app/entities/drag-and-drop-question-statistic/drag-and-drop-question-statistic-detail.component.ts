import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IDragAndDropQuestionStatistic } from 'app/shared/model/drag-and-drop-question-statistic.model';

@Component({
    selector: 'jhi-drag-and-drop-question-statistic-detail',
    templateUrl: './drag-and-drop-question-statistic-detail.component.html'
})
export class DragAndDropQuestionStatisticDetailComponent implements OnInit {
    dragAndDropQuestionStatistic: IDragAndDropQuestionStatistic;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ dragAndDropQuestionStatistic }) => {
            this.dragAndDropQuestionStatistic = dragAndDropQuestionStatistic;
        });
    }

    previousState() {
        window.history.back();
    }
}
