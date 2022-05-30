import { Component, Input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { Exam } from 'app/entities/exam.model';
import { ChartData, getCurrentAmountOfStudentsPerExercises, insertNgxDataAndColorForExerciseMap } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import * as shape from 'd3-shape';

@Component({
    selector: 'jhi-exercise-chart',
    templateUrl: './exercise-chart.component.html',
})
export class ExerciseChartComponent implements OnInit, OnChanges {
    // Input
    @Input()
    exam: Exam;
    @Input()
    examActions: ExamAction[] = [];

    // Chart
    ngxData: ChartData[] = [];
    ngxColor = {
        name: 'Amount of students per exercise grouped by exercise group',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    } as Color;
    legend = false;
    curve: any = shape.curveMonotoneX;

    // Component
    routerLink: any[];
    readonly chart = 'exercise-chart';

    constructor() {}

    ngOnInit(): void {
        this.initData();
        // this.routerLink = ['/course-management', this.exam.course!.id!, 'exams', this.exam.id, 'exercise-groups'];
    }

    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    ngOnChanges(changes: SimpleChanges): void {
        this.initData();
    }

    /**
     * Create and initialize the data for the chart.
     */
    initData() {
        const exerciseAmountMap = getCurrentAmountOfStudentsPerExercises(this.examActions);
        insertNgxDataAndColorForExerciseMap(this.exam, exerciseAmountMap, this.ngxData, this.ngxColor);
    }
}
