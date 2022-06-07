import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { getColor, groupActionsByTimestamp } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';

@Component({
    selector: 'jhi-average-actions-chart',
    templateUrl: './average-actions-chart.component.html',
})
export class AverageActionsChartComponent extends ChartComponent implements OnInit, OnChanges {
    // Input
    @Input()
    examActions: ExamAction[];
    @Input()
    registeredStudents: number;

    // Component
    readonly chart = 'average-actions-chart';

    constructor(private artemisDatePipe: ArtemisDatePipe) {
        super('average-actions-chart', false, [getColor(2)]);
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
        for (const [key, value] of Object.entries(groupedByTimestamp)) {
            // Divide actions per timestamp by amount of registered students
            chartData.push({ name: this.artemisDatePipe.transform(key, 'short'), value: value.length / this.registeredStudents });
        }

        this.ngxData = [{ name: 'actions', series: chartData }];
    }
}
