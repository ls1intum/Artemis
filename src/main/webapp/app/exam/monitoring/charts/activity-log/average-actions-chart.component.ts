import { Component, Input, OnChanges, OnInit, OnDestroy } from '@angular/core';
import { getColor, groupActionsByTimestamp } from 'app/exam/monitoring/charts/monitoring-chart';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { ExamMonitoringWebsocketService } from '../../exam-monitoring-websocket.service';
import { ActivatedRoute } from '@angular/router';
import { ExamAction } from '../../../../entities/exam-user-activity.model';

@Component({
    selector: 'jhi-average-actions-chart',
    templateUrl: './average-actions-chart.component.html',
})
export class AverageActionsChartComponent extends ChartComponent implements OnInit, OnChanges, OnDestroy {
    // Input
    @Input()
    registeredStudents: number;

    constructor(route: ActivatedRoute, examMonitoringWebsocketService: ExamMonitoringWebsocketService, private artemisDatePipe: ArtemisDatePipe) {
        super(route, examMonitoringWebsocketService, 'average-actions-chart', false, [getColor(2)]);
    }

    ngOnInit(): void {
        this.initSubscriptions();
        this.initRenderRate(15);
        this.initData();
    }

    ngOnChanges(): void {
        this.initData();
    }

    ngOnDestroy(): void {
        this.endSubscriptions();
    }

    /**
     * Create and initialize the data for the chart.
     */
    initData() {
        const groupedByTimestamp = groupActionsByTimestamp(this.filteredExamActions);
        const chartData: NgxChartsSingleSeriesDataEntry[] = [];
        for (const [key, value] of Object.entries(groupedByTimestamp)) {
            // Divide actions per timestamp by amount of registered students
            chartData.push({ name: this.artemisDatePipe.transform(key, 'short', true), value: value.length / this.registeredStudents });
        }

        this.ngxData = [{ name: 'actions', series: chartData }];
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    filterRenderedData(examAction: ExamAction): boolean {
        return true;
    }
}
