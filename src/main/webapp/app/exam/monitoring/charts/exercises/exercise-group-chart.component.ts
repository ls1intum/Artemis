import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { Exam } from 'app/entities/exam.model';
import { getColor, getCurrentAmountOfStudentsPerExercises } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction } from 'app/entities/exam-user-activity.model';
import { ChartComponent } from 'app/exam/monitoring/charts/chart.component';

@Component({
    selector: 'jhi-exercise-group-chart',
    templateUrl: './exercise-group-chart.component.html',
})
export class ExerciseGroupChartComponent extends ChartComponent implements OnInit, OnChanges {
    // Input
    @Input()
    exam: Exam;
    @Input()
    examActions: ExamAction[] = [];

    constructor() {
        super('exercise-group-chart', false);
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
        this.exam?.exerciseGroups!.forEach((group, index) => {
            let amount = 0;
            group.exercises?.forEach((exercise) => {
                amount += exerciseAmountMap.get(exercise.id!) ?? 0;
            });
            this.ngxData.push({ name: group.title ?? '', value: amount });
            this.ngxColor.domain.push(getColor(index));
        });
    }
}
