import { Component, Input } from '@angular/core';
import { Color } from '@swimlane/ngx-charts';
import { NgxChartsEntry } from 'app/shared/chart/ngx-charts-datatypes';

@Component({
    selector: 'jhi-exercise-detail-template-chart',
    templateUrl: './exercise-detail-template-chart.component.html',
    styleUrls: ['../monitoring-chart.scss'],
})
export class ExerciseDetailTemplateChartComponent {
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
    @Input()
    reached?: number;
    @Input()
    total?: number;

    constructor() {}

    /**
     * Rounds the number correctly to two decimal digits.
     * https://stackoverflow.com/questions/11832914/how-to-round-to-at-most-2-decimal-places-if-necessary
     * @param num
     */
    roundToTwo(num: number) {
        return Math.round((num + Number.EPSILON) * 100) / 100;
    }
}
