import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { ExamActionService } from '../../exam-action.service';
import { ActivatedRoute } from '@angular/router';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { GraphColors } from 'app/entities/statistics.model';
import { ExamActionType } from 'app/entities/exam-user-activity.model';

@Component({
    selector: 'jhi-students-active-chart',
    templateUrl: './students-active-chart.component.html',
})
export class StudentsActiveChartComponent extends ChartComponent implements OnInit, OnDestroy {
    // Input
    @Input()
    registeredStudents: number;

    reached = 0;

    readonly renderRate = 2;

    constructor(route: ActivatedRoute, examActionService: ExamActionService) {
        super(route, examActionService, 'students-active-chart', false, [GraphColors.GREEN, GraphColors.RED]);
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

        let active = 0;
        const cachedLastAction = this.examActionService.cachedLastActionPerStudent.get(this.examId!) ?? new Map();
        const cachedActions = this.examActionService.cachedActionsPerStudent.get(this.examId!) ?? new Map();
        for (const [key, action] of cachedLastAction.entries()) {
            if (action.type !== ExamActionType.ENDED_EXAM) {
                if (cachedActions.has(key) && cachedActions.get(key).size >= 0) {
                    active++;
                }
            }
        }
        this.ngxData.push({ name: 'Active', value: active } as NgxChartsSingleSeriesDataEntry);
        this.ngxData.push({ name: 'Not active', value: (this.registeredStudents ?? 0) - active } as NgxChartsSingleSeriesDataEntry);
        // Re-trigger change detection
        this.ngxData = [...this.ngxData];
    }
}
