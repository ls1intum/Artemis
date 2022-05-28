import { Component, OnInit, Input } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { ChartData, ChartSeriesData, getColor, groupActionsByTimestamp } from 'app/exam/monitoring/charts/monitoring-chart';
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
        if (this.examActions.length === 0) {
            return;
        }
        const groupedByTimestamp = groupActionsByTimestamp(this.examActions);
        const chartData: ChartData[] = [];
        for (const [key, value] of Object.entries(groupedByTimestamp)) {
            chartData.push(new ChartData(key, value.length));
        }
        // TODO: Remove debug output
        console.log(groupedByTimestamp);
        this.ngxData = [new ChartSeriesData('actions', chartData)];
    }
}
