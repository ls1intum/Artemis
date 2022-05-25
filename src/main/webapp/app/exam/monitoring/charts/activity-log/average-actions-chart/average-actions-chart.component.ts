import { Component, OnInit, Input } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { ChartData } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction } from 'app/entities/exam-user-activity.model';

@Component({
    selector: 'jhi-average-actions-chart',
    templateUrl: './average-actions-chart.component.html',
})
export class AverageActionsChartComponent implements OnInit {
    @Input()
    examActions: ExamAction[];

    // Chart
    ngxData: ChartData[] = [];
    ngxColor = {
        name: 'Average actions over time',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    } as Color;

    constructor() {}

    ngOnInit(): void {
        this.initData();
    }

    initData() {}
}
