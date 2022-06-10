import { Component, Input, OnInit, OnDestroy } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { getCurrentAmountOfStudentsPerExercises, insertNgxDataAndColorForExerciseMap } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction, ExamActionType } from 'app/entities/exam-user-activity.model';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { ExamMonitoringWebsocketService } from '../../exam-monitoring-websocket.service';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-exercise-chart',
    templateUrl: './exercise-chart.component.html',
})
export class ExerciseChartComponent extends ChartComponent implements OnInit, OnDestroy {
    // Input
    @Input()
    exam: Exam;

    readonly renderRate = 5;

    constructor(route: ActivatedRoute, examMonitoringWebsocketService: ExamMonitoringWebsocketService) {
        super(route, examMonitoringWebsocketService, 'exercise-chart', false);
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
        this.ngxData = [];
        this.ngxColor.domain = [];
        const exerciseAmountMap = getCurrentAmountOfStudentsPerExercises(this.filteredExamActions);
        insertNgxDataAndColorForExerciseMap(this.exam, exerciseAmountMap, this.ngxData, this.ngxColor);
        // Re-trigger change detection
        this.ngxData = [...this.ngxData];
        this.ngxColor = Object.assign({}, this.ngxColor);
    }

    filterRenderedData(examAction: ExamAction): boolean {
        return examAction.type === ExamActionType.SWITCHED_EXERCISE;
    }
}
