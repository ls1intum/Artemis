import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { insertNgxDataAndColorForExerciseMap } from 'app/exam/monitoring/charts/monitoring-chart';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { ExamActionService } from '../../exam-action.service';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-exercise-submission-chart',
    templateUrl: './exercise-submission-chart.component.html',
})
export class ExerciseSubmissionChartComponent extends ChartComponent implements OnInit, OnDestroy {
    // Input
    @Input()
    exam: Exam;

    readonly renderRate = 5;

    constructor(route: ActivatedRoute, examActionService: ExamActionService) {
        super(route, examActionService, 'exercise-submission-chart', false);
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

        const exerciseAmountMap: Map<number, number> = new Map();
        const submittedPerStudent = this.examActionService.cachedSubmissionsPerStudent.get(this.examId!) ?? new Map();
        for (const exercises of submittedPerStudent.values()) {
            exercises.forEach((exercise: number | undefined) => {
                if (exercise !== undefined) {
                    exerciseAmountMap.set(exercise, (exerciseAmountMap.get(exercise) ?? 0) + 1);
                }
            });
        }
        insertNgxDataAndColorForExerciseMap(this.exam, exerciseAmountMap, this.ngxData, this.ngxColor);
        // Re-trigger change detection
        this.ngxData = [...this.ngxData];
    }
}
