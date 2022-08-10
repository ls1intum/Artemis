import { Component, OnDestroy, OnInit } from '@angular/core';
import { getColor, groupActionsByTimestamp, groupActionsByType } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction, ExamActionType } from 'app/entities/exam-user-activity.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { NgxChartsMultiSeriesDataEntry, NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { ExamActionService } from '../../exam-action.service';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-category-actions-chart',
    templateUrl: './category-actions-chart.component.html',
})
export class CategoryActionsChartComponent extends ChartComponent implements OnInit, OnDestroy {
    readonly renderRate = 10;

    constructor(route: ActivatedRoute, examActionService: ExamActionService, private artemisDatePipe: ArtemisDatePipe) {
        super(route, examActionService, 'category-actions-chart', true, [getColor(0), getColor(1), getColor(2), getColor(3), getColor(4)]);
    }

    ngOnInit() {
        this.initSubscriptions();
        this.initRenderRate(this.renderRate);
        this.initData();
    }

    ngOnDestroy() {
        this.endSubscriptions();
    }

    /**
     * Create and initialize the data for the chart.
     */
    override initData() {
        super.initData();
        const t0 = performance.now();
        this.createChartData();
        const t1 = performance.now();
        console.log(`${this.chartIdentifierKey}: Create Chart data took ${t1 - t0} milliseconds.`);
    }

    /**
     * Updates the data for the chart.
     */
    override updateData() {
        const t0 = performance.now();
        this.createChartData();
        const t1 = performance.now();
        console.log(`${this.chartIdentifierKey}: Create Chart data took ${t1 - t0} milliseconds.`);
    }

    /**
     * Creates the chart data based on the provided actions.
     * @private
     */
    private createChartData() {
        // Categories
        const categories: Map<string, number> = new Map();

        // Group actions by timestamp
        const groupedByTimestamp = groupActionsByTimestamp(this.filteredExamActions);
        const chartSeriesData: NgxChartsMultiSeriesDataEntry[] = [];
        const chartData: Map<string, NgxChartsSingleSeriesDataEntry[]> = new Map();

        Object.keys(ExamActionType).forEach((type) => {
            chartData.set(type, []);
        });

        for (const timestamp of this.getLastXTimestamps()) {
            Object.keys(ExamActionType).forEach((type) => {
                categories.set(type, 0);
            });

            const key = timestamp.toString();
            if (key in groupedByTimestamp) {
                // Group actions by type
                const groupedByType = groupActionsByType(groupedByTimestamp[key]);

                for (const [typeKey, typeValue] of Object.entries(groupedByType)) {
                    categories.set(typeKey, typeValue.length);
                }
            }

            for (const [category, amount] of categories.entries()) {
                chartData.set(category, [...(chartData.get(category) ?? []), { name: this.artemisDatePipe.transform(timestamp, 'time', true), value: amount }]);
            }
        }

        for (const category of categories.keys()) {
            chartSeriesData.push({ name: category, series: chartData.get(category)! });
            this.ngxColor.domain.push(getColor(Object.keys(ExamActionType).indexOf(category)));
        }
        this.ngxData = chartSeriesData;
    }

    filterRenderedData(examAction: ExamAction) {
        return this.filterActionsNotInTimeframe(examAction);
    }
}
