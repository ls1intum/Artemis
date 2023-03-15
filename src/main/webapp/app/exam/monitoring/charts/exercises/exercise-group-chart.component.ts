import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { ExamActionService } from '../../exam-action.service';
import { Exam } from 'app/entities/exam.model';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { convertCurrentExercisePerStudentMapToNumberOfStudentsPerExerciseMap, getColor, getCurrentExercisePerStudent } from 'app/exam/monitoring/charts/monitoring-chart';

@Component({
    selector: 'jhi-exercise-group-chart',
    templateUrl: './exercise-group-chart.component.html',
})
export class ExerciseGroupChartComponent extends ChartComponent implements OnInit, OnDestroy {
    // Input
    @Input()
    exam: Exam;

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
        this.createChartData();
    }

    /**
     * Updates the data for the chart.
     */
    override updateData() {
        this.createChartData();
    }

    /**
     * Creates the chart data based on the provided actions.
     * @private
     */
    private createChartData() {
        this.ngxData = [];
        this.ngxColor.domain = [];
        const currentExercisePerStudent = getCurrentExercisePerStudent(this.examActionService.cachedLastActionPerStudent.get(this.examId) ?? new Map());
        const exerciseAmountMap = convertCurrentExercisePerStudentMapToNumberOfStudentsPerExerciseMap(currentExercisePerStudent);
        this.exam?.exerciseGroups!.forEach((group, index) => {
            let amount = 0;
            group.exercises?.forEach((exercise) => {
                amount += exerciseAmountMap.get(exercise.id!) ?? 0;
            });
            this.ngxData.push({ name: `${group.title} (${group.id})`, value: amount });
            this.ngxColor.domain.push(getColor(index));
        });
        // Re-trigger change detection
        this.ngxData = [...this.ngxData];
    }
}
