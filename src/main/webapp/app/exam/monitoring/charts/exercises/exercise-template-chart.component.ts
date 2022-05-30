import { Component, Input } from '@angular/core';
import { Color } from '@swimlane/ngx-charts';

@Component({
    selector: 'jhi-exercise-template-chart',
    templateUrl: './exercise-template-chart.component.html',
    styleUrls: ['../monitoring-chart.scss'],
})
export class ExerciseTemplateChartComponent {
    // Input
    @Input()
    ngxData: any[];
    @Input()
    ngxColor: Color;
    @Input()
    curve: any;
    @Input()
    legend = false;
    @Input()
    chart: string;
    @Input()
    routerLink?: any[];

    constructor() {}
}
