import { Component, OnInit, Input } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { ChartData, ChartSeriesData, getColor, groupActionsByTimestamp } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import * as shape from 'd3-shape';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

@Component({
    selector: 'jhi-total-actions-chart',
    templateUrl: './actions-chart.component.html',
    styleUrls: ['../monitoring-chart.scss'],
})
export class TotalActionsChartComponent implements OnInit {
    // Input
    @Input()
    examActions: ExamAction[];

    // Chart
    ngxData: ChartSeriesData[] = [];
    ngxColor = {
        name: 'Total amount of actions',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [getColor(3)],
    } as Color;
    curve: any = shape.curveMonotoneX;
    legend = false;

    // Component
    readonly chart = 'total-actions-chart';

    constructor(private artemisDatePipe: ArtemisDatePipe) {}

    ngOnInit(): void {
        this.initData();
    }

    initData() {
        if (this.examActions.length === 0) {
            return;
        }
        const groupedByTimestamp = groupActionsByTimestamp(this.examActions);
        const chartData: ChartData[] = [];
        let amount = 0;
        for (const [key, value] of Object.entries(groupedByTimestamp)) {
            amount += value.length;
            chartData.push(new ChartData(this.artemisDatePipe.transform(key, 'short'), amount));
        }
        this.ngxData = [new ChartSeriesData('actions', chartData)];
    }
}
