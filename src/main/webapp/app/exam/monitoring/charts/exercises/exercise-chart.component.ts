import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { getCurrentAmountOfStudentsPerExercises, insertNgxDataAndColorForExerciseMap } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';

@Component({
    selector: 'jhi-exercise-chart',
    templateUrl: './exercise-chart.component.html',
})
export class ExerciseChartComponent extends ChartComponent implements OnInit, OnChanges {
    // Input
    @Input()
    exam: Exam;
    @Input()
    examActions: ExamAction[] = [];

    constructor() {
        super('exercise-chart', false);
    }

    ngOnInit(): void {
        this.initData();
    }

    ngOnChanges(): void {
        this.initData();
    }

    /**
     * Create and initialize the data for the chart.
     */
    override initData() {
        const exerciseAmountMap = getCurrentAmountOfStudentsPerExercises(this.examActions);
        insertNgxDataAndColorForExerciseMap(this.exam, exerciseAmountMap, this.ngxData, this.ngxColor);
    }
}
