import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';
import { ExamActionService } from '../../exam-action.service';
import { ActivatedRoute } from '@angular/router';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { GraphColors } from 'app/entities/statistics.model';

@Component({
    selector: 'jhi-students-started-chart',
    templateUrl: './students-started-chart.component.html',
})
export class StudentsStartedChartComponent extends ChartComponent implements OnInit, OnDestroy {
    // Input
    @Input()
    registeredStudents: number;

    reached = 0;

    readonly renderRate = 2;

    constructor(route: ActivatedRoute, examActionService: ExamActionService) {
        super(route, examActionService, 'students-started-chart', false, [GraphColors.GREEN, GraphColors.RED]);
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

        const startedPerStudent = (this.examActionService.cachedStartedPerStudent.get(this.examId!) ?? new Set()).size;
        this.ngxData.push({ name: 'Started', value: startedPerStudent } as NgxChartsSingleSeriesDataEntry);
        this.ngxData.push({ name: 'Missing', value: (this.registeredStudents ?? 0) - startedPerStudent } as NgxChartsSingleSeriesDataEntry);
        // Re-trigger change detection
        this.ngxData = [...this.ngxData];
    }
}
