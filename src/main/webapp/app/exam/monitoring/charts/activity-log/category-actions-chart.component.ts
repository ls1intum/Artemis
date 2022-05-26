import { Component, OnInit, Input } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { ChartData, ChartSeriesData, getColor } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import * as shape from 'd3-shape';

@Component({
    selector: 'jhi-category-actions-chart',
    templateUrl: './actions-chart.component.html',
    styleUrls: ['../monitoring-chart.scss'],
})
export class CategoryActionsChartComponent implements OnInit {
    // Input
    @Input()
    examActions: ExamAction[];
    @Input()
    width: number;

    // Chart
    ngxData: ChartSeriesData[] = [];
    ngxColor = {
        name: 'Total amount of actions per category',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [getColor(0), getColor(1), getColor(2), getColor(3), getColor(4)],
    } as Color;
    curve: any = shape.curveMonotoneX;
    legend = true;

    // Component
    readonly chart = 'category-actions-chart';

    constructor() {}

    ngOnInit(): void {
        this.initData();
    }

    initData() {
        // this.ngxData = mapExamActions(this.examActions);
        const chartSeriesData1 = new ChartSeriesData('STARTED_EXAM', [
            new ChartData('1', 30),
            new ChartData('2', 35),
            new ChartData('3', 40),
            new ChartData('4', 40),
            new ChartData('5', 40),
            new ChartData('6', 45),
            new ChartData('7', 50),
            new ChartData('8', 50),
        ]);
        const chartSeriesData2 = new ChartSeriesData('SAVED_EXERCISE', [
            new ChartData('1', 0),
            new ChartData('2', 20),
            new ChartData('3', 30),
            new ChartData('4', 35),
            new ChartData('5', 45),
            new ChartData('6', 60),
            new ChartData('7', 65),
            new ChartData('8', 80),
        ]);
        const chartSeriesData3 = new ChartSeriesData('SWITCHED_EXERCISE', [
            new ChartData('1', 5),
            new ChartData('2', 35),
            new ChartData('3', 40),
            new ChartData('4', 45),
            new ChartData('5', 50),
            new ChartData('6', 55),
            new ChartData('7', 60),
            new ChartData('8', 80),
        ]);
        const chartSeriesData4 = new ChartSeriesData('ENDED_EXAM', [
            new ChartData('1', 0),
            new ChartData('2', 0),
            new ChartData('3', 0),
            new ChartData('4', 2),
            new ChartData('5', 0),
            new ChartData('6', 0),
            new ChartData('7', 15),
            new ChartData('8', 40),
        ]);
        this.ngxData = [chartSeriesData1, chartSeriesData2, chartSeriesData3, chartSeriesData4];
    }
}
