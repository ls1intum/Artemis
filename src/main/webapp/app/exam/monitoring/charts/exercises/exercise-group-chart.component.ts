import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { getColor, getCurrentAmountOfStudentsPerExercises } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction, ExamActionType } from 'app/entities/exam-user-activity.model';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { ExamMonitoringWebsocketService } from '../../exam-monitoring-websocket.service';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-exercise-group-chart',
    templateUrl: './exercise-group-chart.component.html',
})
export class ExerciseGroupChartComponent extends ChartComponent implements OnInit, OnDestroy {
    // Input
    @Input()
    exam: Exam;

    readonly renderRate = 5;

    constructor(route: ActivatedRoute, examMonitoringWebsocketService: ExamMonitoringWebsocketService) {
        super(route, examMonitoringWebsocketService, 'exercise-group-chart', false);
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
        this.ngxColor = Object.assign({}, this.ngxColor);
    }

    filterRenderedData(examAction: ExamAction): boolean {
        return examAction.type === ExamActionType.SWITCHED_EXERCISE;
    }
}
