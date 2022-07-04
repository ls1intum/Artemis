import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { getSavedExerciseActionsGroupedByActivityId, insertNgxDataAndColorForExerciseMap } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction, ExamActionType, SavedExerciseAction } from 'app/entities/exam-user-activity.model';
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

    submittedPerStudent: Map<number, Set<number>> = new Map();

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
        super.initData();
        const groupedByActivityId = getSavedExerciseActionsGroupedByActivityId(this.filteredExamActions);

        for (const [activityId, values] of Object.entries(groupedByActivityId)) {
            const submitted: Set<number> = new Set();
            values.forEach((action: SavedExerciseAction) => submitted.add(action.exerciseId!));
            this.submittedPerStudent.set(Number(activityId), submitted);
        }
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
        for (const exercises of this.submittedPerStudent.values()) {
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
        const submitted = this.submittedPerStudent.get(examAction.examActivityId!) ?? new Set();
        submitted.add((examAction as SavedExerciseAction).exerciseId!);
        this.submittedPerStudent.set(examAction.examActivityId!, submitted);
    }

    filterRenderedData(examAction: ExamAction) {
        return examAction.type === ExamActionType.SAVED_EXERCISE;
    }
}
