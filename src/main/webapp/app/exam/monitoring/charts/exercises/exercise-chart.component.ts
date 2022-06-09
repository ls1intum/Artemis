import { Component, Input, OnChanges, OnInit, OnDestroy } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { getCurrentAmountOfStudentsPerExercises, insertNgxDataAndColorForExerciseMap } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { ExamMonitoringWebsocketService } from '../../exam-monitoring-websocket.service';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-exercise-chart',
    templateUrl: './exercise-chart.component.html',
})
export class ExerciseChartComponent extends ChartComponent implements OnInit, OnChanges, OnDestroy {
    // Input
    @Input()
    exam: Exam;

    constructor(route: ActivatedRoute, examMonitoringWebsocketService: ExamMonitoringWebsocketService) {
        super(route, examMonitoringWebsocketService, 'exercise-chart', false);
    }

    ngOnInit(): void {
        this.initSubscriptions();
        this.initRenderRate(15);
        this.initData();
    }

    ngOnChanges(): void {
        this.initData();
    }

    ngOnDestroy(): void {
        this.endSubscriptions();
    }

    /**
     * Create and initialize the data for the chart.
     */
    override initData() {
        const exerciseAmountMap = getCurrentAmountOfStudentsPerExercises(this.filteredExamActions);
        insertNgxDataAndColorForExerciseMap(this.exam, exerciseAmountMap, this.ngxData, this.ngxColor);
        // Re-trigger change detection
        this.ngxData = [...this.ngxData];
        this.ngxColor = Object.assign({}, this.ngxColor);
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    filterRenderedData(examAction: ExamAction): boolean {
        return true;
    }
}
