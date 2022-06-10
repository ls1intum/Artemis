import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { getSavedExerciseActionsGroupedByActivityId, insertNgxDataAndColorForExerciseMap } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction, ExamActionType, SavedExerciseAction } from 'app/entities/exam-user-activity.model';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { ExamMonitoringWebsocketService } from '../../exam-monitoring-websocket.service';
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

    constructor(route: ActivatedRoute, examMonitoringWebsocketService: ExamMonitoringWebsocketService) {
        super(route, examMonitoringWebsocketService, 'exercise-submission-chart', false);
    }

    ngOnInit(): void {
        this.initSubscriptions();
        this.initRenderRate(this.renderRate);
        this.initData();
    }

    ngOnDestroy(): void {
        this.endSubscriptions();
    }

    /**
     * Create and initialize the data for the chart.
     */
    initData() {
        this.ngxData = [];
        this.ngxColor.domain = [];
        const exerciseAmountMap: Map<number, number> = new Map();
        const groupedByActivityId = getSavedExerciseActionsGroupedByActivityId(this.filteredExamActions);

        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        for (const [_, value] of Object.entries(groupedByActivityId)) {
            const saved: Map<number, number> = new Map();
            value.forEach((action: SavedExerciseAction) => saved.set(action.exerciseId!, 1));
            for (const key of saved.keys()) {
                exerciseAmountMap.set(key, (exerciseAmountMap.get(key) ?? 0) + 1);
            }
        }
        insertNgxDataAndColorForExerciseMap(this.exam, exerciseAmountMap, this.ngxData, this.ngxColor);
        // Re-trigger change detection
        this.ngxData = [...this.ngxData];
        this.ngxColor = Object.assign({}, this.ngxColor);
    }

    filterRenderedData(examAction: ExamAction): boolean {
        return examAction.type === ExamActionType.SAVED_EXERCISE;
    }
}
