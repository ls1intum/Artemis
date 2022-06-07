import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { getColor, groupActionsByTimestamp, groupActionsByType } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction, ExamActionType } from 'app/entities/exam-user-activity.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { NgxChartsMultiSeriesDataEntry, NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';

@Component({
    selector: 'jhi-category-actions-chart',
    templateUrl: './category-actions-chart.component.html',
})
export class CategoryActionsChartComponent extends ChartComponent implements OnInit, OnChanges {
    // Input
    @Input()
    examActions: ExamAction[];

    constructor(private artemisDatePipe: ArtemisDatePipe) {
        super('category-actions-chart', true, [getColor(0), getColor(1), getColor(2), getColor(3), getColor(4)]);
    }

    ngOnInit(): void {
        this.initData();
    }

    ngOnChanges(): void {
        this.initData();
    }

    /**
     * Create and initialize the data for the chart.
     */
    initData() {
        // Categories
        const categories: Map<string, number> = new Map();

        // Group actions by timestamp
        const groupedByTimestamp = groupActionsByTimestamp(this.examActions);
        const chartSeriesData: NgxChartsMultiSeriesDataEntry[] = [];
        const chartData: Map<string, NgxChartsSingleSeriesDataEntry[]> = new Map();

        Object.keys(ExamActionType).forEach((type) => {
            categories.set(type, 0);
            chartData.set(type, []);
        });

        for (const [timestampKey, timestampValue] of Object.entries(groupedByTimestamp)) {
            // Group actions by type
            const groupedByType = groupActionsByType(timestampValue);

            for (const [typeKey, typeValue] of Object.entries(groupedByType)) {
                categories.set(typeKey, categories.get(typeKey)! + typeValue.length);
            }

            for (const [category, amount] of categories.entries()) {
                chartData.set(category, [...(chartData.get(category) ?? []), { name: this.artemisDatePipe.transform(timestampKey, 'short'), value: amount }]);
            }
        }

        for (const category of categories.keys()) {
            chartSeriesData.push({ name: category, series: chartData.get(category)! });
            this.ngxColor.domain.push(getColor(Object.keys(ExamActionType).indexOf(category)));
        }
        this.ngxData = chartSeriesData;
    }
}
