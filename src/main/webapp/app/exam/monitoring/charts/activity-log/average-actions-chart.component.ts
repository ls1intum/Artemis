import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { getColor, groupActionsByTimestamp } from 'app/exam/monitoring/charts/monitoring-chart';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { ExamMonitoringWebsocketService } from '../../exam-monitoring-websocket.service';
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

    readonly renderRate = 5;

    constructor(route: ActivatedRoute, examMonitoringWebsocketService: ExamMonitoringWebsocketService, private artemisDatePipe: ArtemisDatePipe) {
        super(route, examMonitoringWebsocketService, 'average-actions-chart', false, [getColor(2)]);
    }

    ngOnInit(): void {
        this.initSubscriptions();
        this.initRenderRate(this.renderRate);
        this.initData();
    }

    ngOnDestroy(): void {
        this.endSubscriptions();
    }

    /**
     * Create and initialize the data for the chart.
     */
    override initData() {
        this.createChartData();
    }

    /**
     * Updates the data for the chart.
     */
    override updateData() {
        this.createChartData();
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

    filterRenderedData(examAction: ExamAction): boolean {
        return this.filterActionsNotInTimeframe(examAction);
    }
}
