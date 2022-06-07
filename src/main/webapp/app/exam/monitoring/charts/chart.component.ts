import { Color, ScaleType } from '@swimlane/ngx-charts';
import * as shape from 'd3-shape';
import { NgxChartsEntry } from 'app/shared/chart/ngx-charts-datatypes';

export abstract class ChartComponent {
    chartIdentifierKey = '';

    // Chart
    ngxData: NgxChartsEntry[] = [];
    ngxColor: Color;
    curve: any = shape.curveMonotoneX;
    legend: boolean;
    routerLink: any[];

    protected constructor(chartIdentifierKey: string, legend: boolean, colors?: string[]) {
        this.chartIdentifierKey = chartIdentifierKey;
        this.legend = legend;

        this.ngxColor = {
            name: this.chartIdentifierKey,
            selectable: true,
            group: ScaleType.Ordinal,
            domain: colors ?? [],
        };
    }

    /**
     * Create and initialize the data for the chart.
     */
    abstract initData(): void;
}
