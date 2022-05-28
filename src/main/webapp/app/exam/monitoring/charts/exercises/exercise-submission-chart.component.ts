import { Component, OnInit, Input } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { Exam } from 'app/entities/exam.model';
import { ChartData, getColor, getSavedExerciseActionsGroupedByActivityId } from 'app/exam/monitoring/charts/monitoring-chart';
import { ExamAction, SwitchedExerciseAction } from 'app/entities/exam-user-activity.model';

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
        if (this.examActions.length === 0) {
            return;
        }
        const exerciseAmountMap: Map<number, number> = new Map();
        const groupedByActivityId = getSavedExerciseActionsGroupedByActivityId(this.examActions);

        for (const [_, value] of Object.entries(groupedByActivityId)) {
            const saved: Map<number, number> = new Map();
            value.forEach((action: SwitchedExerciseAction) => saved.set(action.exerciseId!, 1));
            saved.forEach((key) => exerciseAmountMap.set(key, (exerciseAmountMap.get(key) ?? 0) + 1));
        }
        this.exam.exerciseGroups!.forEach((group, index) => {
            group.exercises!.forEach((exercise) => {
                this.ngxData.push(new ChartData(exercise.title ?? '', exerciseAmountMap.get(exercise.id!) ?? 0));
                this.ngxColor.domain.push(getColor(index));
            });
        });
    }
}
