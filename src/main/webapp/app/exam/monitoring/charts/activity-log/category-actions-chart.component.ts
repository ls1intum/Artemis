import { Component, OnInit, Input } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { ChartData, ChartSeriesData, getColor, groupActionsByTimestamp, groupActionsByType } from 'app/exam/monitoring/charts/monitoring-chart';
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
        if (this.examActions.length === 0) {
            return;
        }
        // Group actions by type
        const groupedByType = groupActionsByType(this.examActions);
        const chartSeriesData: ChartSeriesData[] = [];
        for (const [typeKey, typeValue] of Object.entries(groupedByType)) {
            const chartData: ChartData[] = [];
            // Group actions by timestamp
            const groupedByTimestamp = groupActionsByTimestamp(typeValue);

            for (const [timestampKey, timestampValue] of Object.entries(groupedByTimestamp)) {
                chartData.push(new ChartData(timestampKey, timestampValue.length));
            }
            chartSeriesData.push(new ChartSeriesData(typeKey, chartData));
        }
        // TODO: Remove debug output
        console.log(groupedByType);
        this.ngxData = chartSeriesData;
    }
}
