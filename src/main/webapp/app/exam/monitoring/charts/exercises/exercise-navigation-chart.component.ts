import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { getSwitchedExerciseActionsGroupedByActivityId, insertNgxDataAndColorForExerciseMap } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction, SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';

@Component({
    selector: 'jhi-exercise-navigation-chart',
    templateUrl: './exercise-navigation-chart.component.html',
})
export class ExerciseNavigationChartComponent extends ChartComponent implements OnInit, OnChanges {
    // Input
    @Input()
    exam: Exam;
    @Input()
    examActions: ExamAction[] = [];

    constructor() {
        super('exercise-navigation-chart', false);
    }

    ngOnInit(): void {
        this.initData();
    }

    ngOnChanges(): void {
        this.initData();
    }

    /**
     * Create and initialize the data for the chart.
     */
    initData() {
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
