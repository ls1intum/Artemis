import { Component, OnInit, Input } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { Exam } from 'app/entities/exam.model';
import { ChartData, getColor, getSavedExerciseActionsGroupedByActivityId } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction, SavedExerciseAction, SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';

@Component({
    selector: 'jhi-exercise-submission-chart',
    templateUrl: './exercise-chart.component.html',
    styleUrls: ['../monitoring-chart.scss'],
})
export class ExerciseSubmissionChartComponent implements OnInit {
    // Input
    @Input()
    exam: Exam;
    @Input()
    examActions: ExamAction[];

    // Chart
    ngxData: ChartData[] = [];
    ngxColor = {
        name: 'Amount of first submissions per exercise',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    } as Color;
    legend = false;

    // Component
    routerLink: any[];
    readonly chart = 'exercise-submission-chart';

    constructor() {}

    ngOnInit(): void {
        this.initData();
    }

    initData() {
        this.exam.exerciseGroups!.forEach((group, index) => {
            group.exercises!.forEach((exercise) => {
                // TODO: Replace with real data
                this.ngxData.push(new ChartData(exercise.title ?? '', Math.floor(Math.random() * 100)));
                this.ngxColor.domain.push(getColor(index));
            });
        });
    }
}
