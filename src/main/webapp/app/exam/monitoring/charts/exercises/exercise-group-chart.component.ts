import { Component, OnInit, Input, OnChanges, SimpleChanges } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { Exam } from 'app/entities/exam.model';
import { ChartData, getCurrentAmountOfStudentsPerExercises, getColor } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction } from 'app/entities/exam-user-activity.model';

@Component({
    selector: 'jhi-exercise-group-chart',
    templateUrl: './exercise-chart.component.html',
    styleUrls: ['../monitoring-chart.scss'],
})
export class ExerciseGroupChartComponent implements OnInit, OnChanges {
    // Input
    @Input()
    exam: Exam;
    @Input()
    examActions: ExamAction[];

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
    readonly chart = 'exercise-group-chart';

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
        if (this.examActions.length === 0) {
            return;
        }
        const exerciseAmountMap = getCurrentAmountOfStudentsPerExercises(this.examActions);
        this.exam.exerciseGroups!.forEach((group, index) => {
            let amount = 0;
            group.exercises!.forEach((exercise) => {
                amount += exerciseAmountMap.get(exercise.id!) ?? 0;
            });
            this.ngxData.push(new ChartData(group.title ?? '', amount));
            this.ngxColor.domain.push(getColor(index));
        });
    }
}
