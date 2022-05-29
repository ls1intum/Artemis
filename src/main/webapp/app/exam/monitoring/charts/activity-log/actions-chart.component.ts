import { Component, Input } from '@angular/core';
import { Color } from '@swimlane/ngx-charts';
import { ChartSeriesData } from 'app/exam/monitoring/charts/monitoring-chart';

@Component({
    selector: 'jhi-actions-chart',
    templateUrl: './actions-chart.component.html',
    styleUrls: ['../monitoring-chart.scss'],
})
export class ActionsChartComponent {
    // Input
    @Input()
    ngxData: ChartSeriesData[] = [];
    @Input()
    ngxColor: Color;
    @Input()
    curve: any;
    @Input()
    legend = false;
    @Input()
    chart: string;

    constructor() {}
}
