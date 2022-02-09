import { Directive } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { axisTickFormattingWithPercentageSign } from 'app/shared/statistics-graph/statistics-graph.utils';

@Directive()
export class ProgrammingGradingChartsDirective {
    tableFiltered = false;

    ngxColors = {
        name: 'programming grading',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    } as Color;

    readonly xAxisFormatting = axisTickFormattingWithPercentageSign;
    static RESET_TABLE = -5; // we use the number -5 in order to indicate programming-exercise-configure-grading.component.ts to reset the corresponding table

    resetTableFilter() {
        this.tableFiltered = false;
    }
}
