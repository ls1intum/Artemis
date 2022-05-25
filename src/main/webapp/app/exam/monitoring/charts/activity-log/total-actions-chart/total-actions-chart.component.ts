import { Component, OnInit, Input } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { ChartData } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction } from 'app/entities/exam-user-activity.model';

@Component({
    selector: 'jhi-total-actions-chart',
    templateUrl: './total-actions-chart.component.html',
})
export class TotalActionsChartComponent implements OnInit {
    @Input()
    examActions: ExamAction[];

    // Chart
    ngxData: ChartData[] = [];
    ngxColor = {
        name: 'Total actions over time',
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
