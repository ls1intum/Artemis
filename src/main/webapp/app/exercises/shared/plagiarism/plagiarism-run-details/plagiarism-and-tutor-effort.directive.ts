import { Directive } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { yAxisTickFormatting } from 'app/shared/statistics-graph/statistics-graph.utils';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';

@Directive()
export abstract class PlagiarismAndTutorEffortDirective {
    ngxChartLabels: string[];
    /**
     * The similarity distribution is visualized in a bar chart.
     */
    ngxData: NgxChartsSingleSeriesDataEntry[] = [];
    ngxColor = {
        name: 'similarity distribution',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    } as Color;
    readonly yAxisTickFormatting = yAxisTickFormatting;

    /**
     * Formats the labels on the y axis in order to display only integer values
     * @param tick the default y axis tick
     * @returns modified y axis tick
     */
    /*yAxisTickFormatting(tick: string): string {
        return parseFloat(tick).toFixed(0);
    }*/
}
