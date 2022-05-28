import { Component, Input, OnInit } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { Exam } from 'app/entities/exam.model';
import { ChartData, getCurrentAmountOfStudentsPerExercises, getColor } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction, ExamActionType } from 'app/entities/exam-user-activity.model';

@Component({
    selector: 'jhi-exercise-chart',
    templateUrl: './exercise-chart.component.html',
    styleUrls: ['../monitoring-chart.scss'],
})
export class ExerciseChartComponent implements OnInit {
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

    initData() {
        if (this.examActions.length === 0) {
            return;
        }
        const exerciseAmountMap = getCurrentAmountOfStudentsPerExercises(this.examActions);
        this.exam.exerciseGroups!.forEach((group, index) => {
            group.exercises!.forEach((exercise) => {
                this.ngxData.push(new ChartData(exercise.title ?? '', exerciseAmountMap.get(exercise.id!) ?? 0));
                this.ngxColor.domain.push(getColor(index));
            });
        });
    }
}
