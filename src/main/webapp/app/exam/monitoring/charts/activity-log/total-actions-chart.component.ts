import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { getColor, groupActionsByTimestamp } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';

@Component({
    selector: 'jhi-total-actions-chart',
    templateUrl: './total-actions-chart.component.html',
})
export class TotalActionsChartComponent extends ChartComponent implements OnInit, OnChanges {
    // Input
    @Input()
    examActions: ExamAction[];

    constructor(private artemisDatePipe: ArtemisDatePipe) {
        super('total-actions-chart', false, [getColor(2)]);
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
        const groupedByTimestamp = groupActionsByTimestamp(this.examActions);
        const chartData: NgxChartsSingleSeriesDataEntry[] = [];
        let amount = 0;
        for (const [key, value] of Object.entries(groupedByTimestamp)) {
            amount += value.length;
            chartData.push({ name: this.artemisDatePipe.transform(key, 'short'), value: amount });
        }
        this.ngxData = [{ name: 'actions', series: chartData }];
    }
}
