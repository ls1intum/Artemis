import { Component, Input } from '@angular/core';
import { Color } from '@swimlane/ngx-charts';
import { NgxChartsEntry } from 'app/shared/chart/ngx-charts-datatypes';

@Component({
    selector: 'jhi-exercise-template-chart',
    templateUrl: './exercise-template-chart.component.html',
    styleUrls: ['../live-statistics-chart.scss'],
})
export class ExerciseTemplateChartComponent {
    // Input
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

    constructor() {}
}
