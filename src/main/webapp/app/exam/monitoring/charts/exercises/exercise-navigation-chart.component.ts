import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { getSwitchedExerciseActionsGroupedByActivityId, insertNgxDataAndColorForExerciseMap } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction, ExamActionType, SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { ExamMonitoringWebsocketService } from '../../exam-monitoring-websocket.service';
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

    constructor(route: ActivatedRoute, examMonitoringWebsocketService: ExamMonitoringWebsocketService) {
        super(route, examMonitoringWebsocketService, 'exercise-navigation-chart', false);
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
        const groupedByActivityId = getSwitchedExerciseActionsGroupedByActivityId(this.filteredExamActions);

        // eslint-disable-next-line @typescript-eslint/no-unused-vars
        for (const [_, value] of Object.entries(groupedByActivityId)) {
            const navigatedTo: Map<number, number> = new Map();
            value.forEach((action: SwitchedExerciseAction) => navigatedTo.set(action.exerciseId!, 1));
            for (const key of navigatedTo.keys()) {
                exerciseAmountMap.set(key, (exerciseAmountMap.get(key) ?? 0) + 1);
            }
        }
        insertNgxDataAndColorForExerciseMap(this.exam, exerciseAmountMap, this.ngxData, this.ngxColor);
        // Re-trigger change detection
        this.ngxData = [...this.ngxData];
        this.ngxColor = Object.assign({}, this.ngxColor);
    }

    filterRenderedData(examAction: ExamAction): boolean {
        return examAction.type === ExamActionType.SWITCHED_EXERCISE;
    }
}
