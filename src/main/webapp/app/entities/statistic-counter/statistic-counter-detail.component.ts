import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IStatisticCounter } from 'app/shared/model/statistic-counter.model';

@Component({
    selector: 'jhi-statistic-counter-detail',
    templateUrl: './statistic-counter-detail.component.html'
})
export class StatisticCounterDetailComponent implements OnInit {
    statisticCounter: IStatisticCounter;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ statisticCounter }) => {
            this.statisticCounter = statisticCounter;
        });
    }

    previousState() {
        window.history.back();
    }
}
