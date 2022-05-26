import { Component, OnInit, Input } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { Exam } from 'app/entities/exam.model';
import { ChartData, getColor } from 'app/exam/monitoring/charts/monitoring-chart';

@Component({
    selector: 'jhi-exercise-chart',
    templateUrl: './exercise-chart.component.html',
    styleUrls: ['../monitoring-chart.scss'],
})
export class ExerciseChartComponent implements OnInit {
    // Input
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

    // Component
    routerLink: any[];
    readonly chart = 'exercise-chart';

    constructor() {}

    ngOnInit(): void {
        this.initData();
        this.routerLink = ['/course-management', this.exam.course!.id!, 'exams', this.exam.id, 'exercise-groups'];
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
