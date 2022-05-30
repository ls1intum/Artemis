import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { Exam } from 'app/entities/exam.model';
import { ChartData, getSavedExerciseActionsGroupedByActivityId, insertNgxDataAndColorForExerciseMap } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction, SavedExerciseAction, SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';

@Component({
    selector: 'jhi-exercise-submission-chart',
    templateUrl: './exercise-submission-chart.component.html',
})
export class ExerciseSubmissionChartComponent implements OnInit, OnChanges {
    // Input
    @Input()
    exam: Exam;
    @Input()
    examActions: ExamAction[] = [];

    // Chart
    ngxData: ChartData[] = [];
    ngxColor = {
        name: 'Amount of first submissions per exercise',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    } as Color;
    legend = false;

    // Component
    routerLink: any[];
    readonly chart = 'exercise-submission-chart';

    constructor() {}

    ngOnInit(): void {
        this.initData();
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    ngOnChanges(changes: SimpleChanges): void {
        this.initData();
    }

    /**
     * Create and initialize the data for the chart.
     */
    initData() {
        const exerciseAmountMap: Map<number, number> = new Map();
        const groupedByActivityId = getSavedExerciseActionsGroupedByActivityId(this.examActions);

        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        for (const [_, value] of Object.entries(groupedByActivityId)) {
            const saved: Map<number, number> = new Map();
            value.forEach((action: SavedExerciseAction) => saved.set(action.exerciseId!, 1));
            for (const key of saved.keys()) {
                exerciseAmountMap.set(key, (exerciseAmountMap.get(key) ?? 0) + 1);
            }
        }
        insertNgxDataAndColorForExerciseMap(this.exam, exerciseAmountMap, this.ngxData, this.ngxColor);
    }
}
