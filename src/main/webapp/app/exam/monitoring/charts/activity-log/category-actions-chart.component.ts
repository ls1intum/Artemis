import { Component, OnInit, Input } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { ChartData, ChartSeriesData, getColor, groupActionsByTimestamp, groupActionsByType } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction, ExamActionType } from 'app/entities/exam-user-activity.model';
import * as shape from 'd3-shape';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

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

    constructor(private artemisDatePipe: ArtemisDatePipe) {}

    ngOnInit(): void {
        this.initData();
    }

    initData() {
        if (this.examActions.length === 0) {
            return;
        }
        // Categories
        const categories: Map<string, number> = new Map();
        Object.keys(ExamActionType).forEach((type) => categories.set(type, 0));

        // Group actions by timestamp
        const groupedByTimestamp = groupActionsByTimestamp(this.examActions);
        const chartSeriesData: ChartSeriesData[] = [];
        const chartData: Map<string, ChartData[]> = new Map();

        for (const [timestampKey, timestampValue] of Object.entries(groupedByTimestamp)) {
            // Group actions by type
            const groupedByType = groupActionsByType(timestampValue);

            for (const [typeKey, typeValue] of Object.entries(groupedByType)) {
                categories.set(typeKey, categories.get(typeKey)! + typeValue.length);
            }

            for (const [category, amount] of categories.entries()) {
                chartData.set(category, [...(chartData.get(category) ?? []), new ChartData(this.artemisDatePipe.transform(timestampKey, 'short'), amount)]);
            }
        }

        for (const category of categories.keys()) {
            chartSeriesData.push(new ChartSeriesData(category, chartData.get(category)!));
        }
        this.ngxData = chartSeriesData;
    }
}
