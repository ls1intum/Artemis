import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { ExamActionService } from '../../exam-action.service';
import { ActivatedRoute } from '@angular/router';
import { Exercise } from 'app/entities/exercise.model';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { GraphColors } from 'app/entities/statistics.model';

@Component({
    selector: 'jhi-exercise-detail-submission-chart',
    templateUrl: './exercise-detail-submission-chart.component.html',
})
export class ExerciseDetailSubmissionChartComponent extends ChartComponent implements OnInit, OnDestroy {
    // Input
    @Input()
    exam: Exam;

    // Input
    @Input()
    exercise: Exercise;

    reached = 0;

    readonly renderRate = 2;

    constructor(route: ActivatedRoute, examActionService: ExamActionService) {
        super(route, examActionService, 'exercise-detail-submission-chart', false, [GraphColors.GREEN, GraphColors.RED]);
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

        let submissions = 0;
        const submittedPerStudent = this.examActionService.cachedSubmissionsPerStudent.get(this.examId!) ?? new Map();
        for (const exercises of submittedPerStudent.values()) {
            exercises.forEach((exercise: number | undefined) => {
                if (exercise !== undefined && exercise === this.exercise.id) {
                    submissions++;
                }
            });
        }
        this.reached = submissions;
        this.ngxData.push({ name: 'Submissions', value: submissions } as NgxChartsSingleSeriesDataEntry);
        this.ngxData.push({ name: 'Missing', value: (this.exam.numberOfRegisteredUsers ?? 0) - submissions } as NgxChartsSingleSeriesDataEntry);
        // Re-trigger change detection
        this.ngxData = [...this.ngxData];
    }
}
