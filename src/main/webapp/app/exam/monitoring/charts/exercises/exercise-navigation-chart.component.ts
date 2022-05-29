import { Component, OnInit, Input, OnChanges, SimpleChanges } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { Exam } from 'app/entities/exam.model';
import { ChartData, getSwitchedExerciseActionsGroupedByActivityId, insertNgxDataAndColorForExerciseMap } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction, SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';

@Component({
    selector: 'jhi-exercise-navigation-chart',
    templateUrl: './exercise-chart.component.html',
    styleUrls: ['../monitoring-chart.scss'],
})
export class ExerciseNavigationChartComponent implements OnInit, OnChanges {
    // Input
    @Input()
    exam: Exam;
    @Input()
    examActions: ExamAction[];

    // Chart
    ngxData: ChartData[] = [];
    ngxColor = {
        name: 'Amount of navigations into the specific exercise',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    } as Color;
    legend = false;

    // Component
    routerLink: any[];
    readonly chart = 'exercise-navigation-chart';

    constructor() {}

    ngOnInit(): void {
        this.initData();
    }

    ngOnChanges(changes: SimpleChanges): void {
        this.initData();
    }

    /**
     * Create and initialize the data for the chart.
     */
    initData() {
        if (this.examActions.length === 0) {
            return;
        }
        const exerciseAmountMap: Map<number, number> = new Map();
        const groupedByActivityId = getSwitchedExerciseActionsGroupedByActivityId(this.examActions);

        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        for (const [_, value] of Object.entries(groupedByActivityId)) {
            const navigatedTo: Map<number, number> = new Map();
            value.forEach((action: SwitchedExerciseAction) => navigatedTo.set(action.exerciseId!, 1));
            for (const key of navigatedTo.keys()) {
                exerciseAmountMap.set(key, (exerciseAmountMap.get(key) ?? 0) + 1);
            }
        }
        insertNgxDataAndColorForExerciseMap(this.exam, exerciseAmountMap, this.ngxData, this.ngxColor);
    }
}
