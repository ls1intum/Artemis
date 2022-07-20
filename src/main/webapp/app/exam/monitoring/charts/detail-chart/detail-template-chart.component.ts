import { Component, Input } from '@angular/core';
import { Color } from '@swimlane/ngx-charts';
import { NgxChartsEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { round } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-detail-template-chart',
    templateUrl: './detail-template-chart.component.html',
    styleUrls: ['../monitoring-chart.scss'],
})
export class DetailTemplateChartComponent {
    @Input()
    ngxData: NgxChartsEntry[];
    @Input()
    ngxColor: Color;
    @Input()
    curve: any;
    @Input()
    legend = false;
    @Input()
    chartIdentifierKey: string;
    @Input()
    routerLink?: any[];
    @Input()
    reached?: number;
    @Input()
    total?: number;

    constructor() {}

    round = round;
}
