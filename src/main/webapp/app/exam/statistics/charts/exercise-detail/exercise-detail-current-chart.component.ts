import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { ChartComponent } from 'app/exam/statistics/charts/chart.component';
import { ExamActionService } from '../../exam-action.service';
import { ActivatedRoute } from '@angular/router';
import { Exercise } from 'app/entities/exercise.model';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { GraphColors } from 'app/entities/statistics.model';
import { getCurrentExercisePerStudent } from 'app/exam/statistics/charts/live-statistics-chart';

@Component({
    selector: 'jhi-exercise-detail-current-chart',
    templateUrl: './exercise-detail-current-chart.component.html',
})
export class ExerciseDetailCurrentChartComponent extends ChartComponent implements OnInit, OnDestroy {
    @Input()
    exam: Exam;
    @Input()
    exercise: Exercise;

    reached = 0;

    readonly renderRate = 2;

    constructor(route: ActivatedRoute, examActionService: ExamActionService) {
        super(route, examActionService, 'exercise-detail-current-chart', false, [GraphColors.GREEN, GraphColors.RED]);
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

        let currentUser = 0;
        const currentExercisePerStudent = getCurrentExercisePerStudent(this.examActionService.cachedLastActionPerStudent.get(this.examId!) ?? new Map());
        for (const exercise of currentExercisePerStudent.values()) {
            if (exercise !== undefined && exercise === this.exercise.id) {
                currentUser++;
            }
        }
        this.reached = currentUser;
        this.ngxData.push({ name: 'Current user', value: currentUser } as NgxChartsSingleSeriesDataEntry);
        this.ngxData.push({ name: 'Missing', value: (this.exam.numberOfRegisteredUsers ?? 0) - currentUser } as NgxChartsSingleSeriesDataEntry);
        // Re-trigger change detection
        this.ngxData = [...this.ngxData];
    }
}
