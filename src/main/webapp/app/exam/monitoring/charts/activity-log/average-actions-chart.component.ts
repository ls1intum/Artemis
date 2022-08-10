import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { getColor, groupActionsByTimestamp } from 'app/exam/monitoring/charts/monitoring-chart';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { ExamActionService } from '../../exam-action.service';
import { ActivatedRoute } from '@angular/router';
import { ExamAction } from 'app/entities/exam-user-activity.model';

@Component({
    selector: 'jhi-average-actions-chart',
    templateUrl: './average-actions-chart.component.html',
})
export class AverageActionsChartComponent extends ChartComponent implements OnInit, OnDestroy {
    // Input
    @Input()
    registeredStudents: number;

    readonly renderRate = 10;

    constructor(route: ActivatedRoute, examActionService: ExamActionService, private artemisDatePipe: ArtemisDatePipe) {
        super(route, examActionService, 'average-actions-chart', false, [getColor(2)]);
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
        const groupedByTimestamp = groupActionsByTimestamp(this.filteredExamActions);
        const chartData: NgxChartsSingleSeriesDataEntry[] = [];
        for (const timestamp of this.getLastXTimestamps()) {
            const key = timestamp.toString();
            let value = 0;
            if (key in groupedByTimestamp) {
                // Divide actions per timestamp by amount of registered students
                value = groupedByTimestamp[key].length / this.registeredStudents;
            }
            chartData.push({ name: this.artemisDatePipe.transform(key, 'time', true), value });
        }

        this.ngxData = [{ name: 'actions', series: chartData }];
    }

    filterRenderedData(examAction: ExamAction) {
        return this.filterActionsNotInTimeframe(examAction);
    }
}
