import { Component, Input } from '@angular/core';
import { Color } from '@swimlane/ngx-charts';
import { NgxChartsEntry } from 'app/shared/chart/ngx-charts-datatypes';

@Component({
    selector: 'jhi-actions-chart',
    templateUrl: './actions-chart.component.html',
    styleUrls: ['../live-statistics-chart.scss'],
})
export class ActionsChartComponent {
    // Input
    @Input()
    ngxData: NgxChartsEntry[] = [];
    @Input()
    ngxColor: Color;
    @Input()
    curve: any;
    @Input()
    legend = false;
    @Input()
    chartIdentifierKey: string;

    constructor() {}
}
