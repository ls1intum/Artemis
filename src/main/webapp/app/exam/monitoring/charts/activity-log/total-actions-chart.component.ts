import { Component, OnDestroy, OnInit } from '@angular/core';
import { getColor, groupActionsByTimestamp } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { ExamMonitoringWebsocketService } from '../../exam-monitoring-websocket.service';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-total-actions-chart',
    templateUrl: './total-actions-chart.component.html',
})
export class TotalActionsChartComponent extends ChartComponent implements OnInit, OnDestroy {
    readonly renderRate = 5;

    constructor(route: ActivatedRoute, examMonitoringWebsocketService: ExamMonitoringWebsocketService, private artemisDatePipe: ArtemisDatePipe) {
        super(route, examMonitoringWebsocketService, 'total-actions-chart', false, [getColor(2)]);
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
        let amount = 0;
        for (const timestamp of this.getLastXTimestamps()) {
            const key = timestamp.toString();
            if (key in groupedByTimestamp) {
                amount += groupedByTimestamp[key].length;
            }
            chartData.push({ name: this.artemisDatePipe.transform(timestamp, 'time', true), value: amount });
        }
        this.ngxData = [{ name: 'actions', series: chartData }];
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    filterRenderedData(examAction: ExamAction): boolean {
        return true;
    }
}
