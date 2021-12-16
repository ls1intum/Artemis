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
    yScaleMax = 5;

    /**
     * Formats the labels on the y axis in order to display only integer values
     * @param tick the default y axis tick
     * @returns modified y axis tick
     */
    yAxisTickFormatting(tick: string): string {
        return parseFloat(tick).toFixed(0);
    }

    /**
     * Determines the upper limit for the y axis
     * @param data the data that should be displayed
     */
    determineMaxChartHeight(data: number[]): void {
        this.yScaleMax = Math.max(this.yScaleMax, ...data);
    }
}
