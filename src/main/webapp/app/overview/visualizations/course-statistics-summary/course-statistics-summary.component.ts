import { Component, Input, OnInit } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { Subscription } from 'rxjs';

const OVERALL_POINTS_COLOR = '#FFFF00';
const REACHABLE_POINTS_COLOR = '#D3D3D3';

interface ChartData {
    name: string;
    value: number;
}
interface StackedSeriesData {
    name: string;
    series: ChartData[];
}
@Component({
    selector: 'jhi-course-statistics-summary',
    templateUrl: './course-statistics-summary.component.html',
    styleUrl: '../../course-overview.scss',
})
export class CourseStatisticsSummaryComponent implements OnInit {
    @Input({ required: true }) currentRelativeScore: number;
    @Input({ required: true }) overallPoints: number;
    @Input({ required: true }) reachablePoints: number;

    colorScheme = {
        name: 'Your overall points color',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [OVERALL_POINTS_COLOR, REACHABLE_POINTS_COLOR],
    } as Color;
    subscriptions: Subscription[];
    data: StackedSeriesData[] = [{ name: 'overAllPoints', series: [] }];

    ngOnInit() {
        const remainingPoints = this.reachablePoints - this.overallPoints;
        this.data[0].series.push(
            {
                name: 'overAllPoints',
                value: this.overallPoints,
            },
            {
                name: 'reachablePoints',
                value: remainingPoints,
            },
        );
        console.log('data', this.data);
    }

    constructor() {}
}
