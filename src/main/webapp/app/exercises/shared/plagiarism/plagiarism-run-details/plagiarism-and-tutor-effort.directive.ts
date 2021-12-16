import { Directive } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';

@Directive()
export abstract class PlagiarismAndTutorEffortDirective {
    ngxChartLabels: string[];
    /**
     * The similarity distribution is visualized in a bar chart.
     */
    ngxData: any[] = [];
    ngxColor = {
        name: 'similarity distribution',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: ['#87cefa'], // color: light blue
    } as Color;
    yAxisTicks: number[];

    /**
     * Formats the labels on the y axis in order to display only integer values
     * @param tick the default y axis tick
     * @returns modified y axis tick
     */
    yAxisTickFormatting(tick: string): string {
        return parseFloat(tick).toFixed(0);
    }

    /**
     * Sets the y axis ticks to the range of discrete integers from 0 up to the maximum value in the data set
     * @param data the data set that is displayed by the chart
     */
    determineYAxisTicks(data: number[]): void {
        const maxValue = Math.max(...data);
        this.yAxisTicks = Array.from(Array(maxValue + 1).keys());
    }
}
