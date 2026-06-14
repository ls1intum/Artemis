import { Directive, signal } from '@angular/core';
import { yAxisTickFormatting } from 'app/exercise/statistics-graph/util/statistics-graph.utils';
import { ChartSeriesEntry } from 'app/shared-ui/chart/chart-data.model';

@Directive()
export abstract class PlagiarismAndTutorEffortDirective {
    chartLabels: string[];
    /**
     * The similarity/effort distribution that is visualized in a bar chart; one entry per bar.
     */
    readonly chartEntries = signal<ChartSeriesEntry[]>([]);
    /**
     * The raw per-bar colors (CSS variable references, see {@code GraphColors}).
     * Subclasses resolve them to concrete colors via {@code ChartColorService.resolvedColors}.
     */
    readonly chartColors = signal<string[]>([]);
    /** Formats the value axis ticks so that only integer values are displayed. */
    readonly yAxisTickFormatting = (tick: number | string) => yAxisTickFormatting(String(tick));
}
