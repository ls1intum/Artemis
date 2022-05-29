import { Component, Input, OnInit, OnChanges, SimpleChanges } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { Exam } from 'app/entities/exam.model';
import { ChartData, getCurrentAmountOfStudentsPerExercises, insertNgxDataAndColorForExerciseMap } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction } from 'app/entities/exam-user-activity.model';

@Component({
    selector: 'jhi-exercise-chart',
    templateUrl: './exercise-chart.component.html',
    styleUrls: ['../monitoring-chart.scss'],
})
export class ExerciseChartComponent implements OnInit, OnChanges {
    // Input
    @Input()
    examActions: ExamAction[];
    @Input()
    exam: Exam;

    // Chart
    ngxData: ChartData[] = [];
    ngxColor = {
        name: 'Amount of students per exercise grouped by exercise group',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    } as Color;
    legend = false;

    // Component
    routerLink: any[];
    readonly chart = 'exercise-chart';

    constructor() {}

    ngOnInit(): void {
        this.initData();
        // this.routerLink = ['/course-management', this.exam.course!.id!, 'exams', this.exam.id, 'exercise-groups'];
    }

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
