import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { IStatistic } from 'app/shared/model/statistic.model';

@Component({
    selector: 'jhi-statistic-detail',
    templateUrl: './statistic-detail.component.html'
})
export class StatisticDetailComponent implements OnInit {
    statistic: IStatistic;

    constructor(private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ statistic }) => {
            this.statistic = statistic;
        });
    }

    previousState() {
        window.history.back();
    }
}
