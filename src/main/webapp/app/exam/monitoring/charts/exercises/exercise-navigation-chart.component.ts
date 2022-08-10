import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { getSwitchedExerciseActionsGroupedByActivityId, insertNgxDataAndColorForExerciseMap } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction, ExamActionType, SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { ExamActionService } from '../../exam-action.service';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-exercise-navigation-chart',
    templateUrl: './exercise-navigation-chart.component.html',
})
export class ExerciseNavigationChartComponent extends ChartComponent implements OnInit, OnDestroy {
    // Input
    @Input()
    exam: Exam;

    readonly renderRate = 5;

    navigatedToPerStudent: Map<number, Set<number | undefined>> = new Map();

    constructor(route: ActivatedRoute, examActionService: ExamActionService) {
        super(route, examActionService, 'exercise-navigation-chart', false);
    }

    ngOnInit() {
        this.initSubscriptions();
        this.initRenderRate(this.renderRate);
        this.initData();
    }

    ngOnDestroy() {
        this.endSubscriptions();
    }

    /**
     * Create and initialize the data for the chart.
     */
    override initData() {
        super.initData();
        const groupedByActivityId = getSwitchedExerciseActionsGroupedByActivityId(this.filteredExamActions);

        for (const [activityId, values] of Object.entries(groupedByActivityId)) {
            const navigatedTo: Set<number> = new Set();
            values.forEach((action: SwitchedExerciseAction) => navigatedTo.add(action.exerciseId!));
            this.navigatedToPerStudent.set(Number(activityId), navigatedTo);
        }
        const t0 = performance.now();
        this.createChartData();
        const t1 = performance.now();
        console.log(`${this.chartIdentifierKey}: Create Chart data took ${t1 - t0} milliseconds.`);
    }

    /**
     * Updates the data for the chart.
     */
    override updateData() {
        const t0 = performance.now();
        this.createChartData();
        const t1 = performance.now();
        console.log(`${this.chartIdentifierKey}: Create Chart data took ${t1 - t0} milliseconds.`);
    }

    /**
     * Creates the chart data based on the provided actions.
     * @private
     */
    private createChartData() {
        this.ngxData = [];
        this.ngxColor.domain = [];

        const exerciseAmountMap: Map<number, number> = new Map();
        for (const exercises of this.navigatedToPerStudent.values()) {
            exercises.forEach((exercise) => {
                if (exercise !== undefined) {
                    exerciseAmountMap.set(exercise, (exerciseAmountMap.get(exercise) ?? 0) + 1);
                }
            });
        }
        insertNgxDataAndColorForExerciseMap(this.exam, exerciseAmountMap, this.ngxData, this.ngxColor);
        // Re-trigger change detection
        this.ngxData = [...this.ngxData];
    }

    override evaluateAndAddAction(examAction: ExamAction) {
        super.evaluateAndAddAction(examAction);
        const navigatedTo = this.navigatedToPerStudent.get(examAction.examActivityId!) ?? new Set();
        navigatedTo.add((examAction as SwitchedExerciseAction).exerciseId);
        this.navigatedToPerStudent.set(examAction.examActivityId!, navigatedTo);
    }

    filterRenderedData(examAction: ExamAction) {
        return examAction.type === ExamActionType.SWITCHED_EXERCISE;
    }
}
