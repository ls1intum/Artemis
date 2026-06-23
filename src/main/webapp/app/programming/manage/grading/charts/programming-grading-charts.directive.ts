import { Directive, signal } from '@angular/core';
import { axisTickFormattingWithPercentageSign } from 'app/exercise/statistics-graph/util/statistics-graph.utils';

@Directive()
export class ProgrammingGradingChartsDirective {
    tableFiltered = false;

    /** Raw colors of the chart segments (one per test case / category), in segment order. */
    readonly chartColors = signal<string[]>([]);

    readonly xAxisFormatting = axisTickFormattingWithPercentageSign;
    static RESET_TABLE = -5; // we use the number -5 in order to indicate programming-exercise-configure-grading.component.ts to reset the corresponding table

    resetTableFilter() {
        this.tableFiltered = false;
    }
}
