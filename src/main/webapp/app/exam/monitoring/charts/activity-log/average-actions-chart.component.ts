import { Component, OnInit, Input } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { ChartData, ChartSeriesData, getColor } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import * as shape from 'd3-shape';

@Component({
    selector: 'jhi-average-actions-chart',
    templateUrl: './actions-chart.component.html',
    styleUrls: ['../monitoring-chart.scss'],
})
export class AverageActionsChartComponent implements OnInit {
    // Input
    @Input()
    examActions: ExamAction[];

    // Chart
    ngxData: ChartSeriesData[] = [];
    ngxColor = {
        name: 'Average amount of actions per student',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [getColor(3)],
    } as Color;
    curve: any = shape.curveMonotoneX;
    legend = false;

    // Component
    readonly chart = 'average-actions-chart';

    constructor() {}

    ngOnInit(): void {
        this.initData();
    }

    initData() {
        // this.ngxData = mapExamActions(this.examActions);
        const chartSeriesData = new ChartSeriesData('test', [
            new ChartData('1', 10),
            new ChartData('2', 15),
            new ChartData('3', 18),
            new ChartData('4', 24),
            new ChartData('5', 26),
            new ChartData('6', 30),
            new ChartData('7', 150),
        ]);
        this.ngxData = [chartSeriesData];
    }
}
