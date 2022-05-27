import { Component, OnInit, Input } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { ChartData, ChartSeriesData, getColor } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import * as shape from 'd3-shape';

@Component({
    selector: 'jhi-total-actions-chart',
    templateUrl: './actions-chart.component.html',
    styleUrls: ['../monitoring-chart.scss'],
})
export class TotalActionsChartComponent implements OnInit {
    // Input
    @Input()
    examActions: ExamAction[];

    // Chart
    ngxData: ChartSeriesData[] = [];
    ngxColor = {
        name: 'Total amount of actions',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [getColor(3)],
    } as Color;
    curve: any = shape.curveMonotoneX;
    legend = false;

    // Component
    readonly chart = 'total-actions-chart';

    constructor() {}

    ngOnInit(): void {
        this.initData();
    }

    initData() {
        // this.ngxData = mapExamActions(this.examActions);
        const chartSeriesData = new ChartSeriesData('test', [
            new ChartData('1', 10),
            new ChartData('2', 15),
            new ChartData('3', 30),
            new ChartData('4', 80),
            new ChartData('5', 95),
            new ChartData('6', 105),
            new ChartData('7', 10500),
        ]);
        this.ngxData = [chartSeriesData];
    }
}
