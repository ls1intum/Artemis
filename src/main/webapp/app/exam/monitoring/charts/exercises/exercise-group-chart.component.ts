import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import {
    convertCurrentExercisePerStudentMapToNumberOfStudentsPerExerciseMap,
    getColor,
    getCurrentExercisePerStudent,
    updateCurrentExerciseOfStudent,
} from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction, ExamActionType } from 'app/entities/exam-user-activity.model';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { ExamActionService } from '../../exam-action.service';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-exercise-group-chart',
    templateUrl: './exercise-group-chart.component.html',
})
export class ExerciseGroupChartComponent extends ChartComponent implements OnInit, OnDestroy {
    // Input
    @Input()
    exam: Exam;

    currentExercisePerStudent: Map<number, number | undefined> = new Map();

    readonly renderRate = 5;

    constructor(route: ActivatedRoute, examActionService: ExamActionService) {
        super(route, examActionService, 'exercise-group-chart', false);
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
        this.currentExercisePerStudent = getCurrentExercisePerStudent(this.filteredExamActions);
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
        const exerciseAmountMap = convertCurrentExercisePerStudentMapToNumberOfStudentsPerExerciseMap(this.currentExercisePerStudent);
        this.exam?.exerciseGroups!.forEach((group, index) => {
            let amount = 0;
            group.exercises?.forEach((exercise) => {
                amount += exerciseAmountMap.get(exercise.id!) ?? 0;
            });
            this.ngxData.push({ name: group.title ?? '', value: amount });
            this.ngxColor.domain.push(getColor(index));
        });
        // Re-trigger change detection
        this.ngxData = [...this.ngxData];
    }

    override evaluateAndAddAction(examAction: ExamAction) {
        super.evaluateAndAddAction(examAction);
        updateCurrentExerciseOfStudent(examAction, this.currentExercisePerStudent);
    }

    filterRenderedData(examAction: ExamAction) {
        return (
            examAction.type === ExamActionType.SWITCHED_EXERCISE ||
            examAction.type === ExamActionType.SAVED_EXERCISE ||
            examAction.type === ExamActionType.ENDED_EXAM ||
            examAction.type === ExamActionType.HANDED_IN_EARLY
        );
    }
}
