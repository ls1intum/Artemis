import { Component, OnInit, Input } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { Exam } from 'app/entities/exam.model';
import { ChartData, getColor } from 'app/exam/monitoring/charts/monitoring-chart';

@Component({
    selector: 'jhi-exercise-chart',
    templateUrl: './exercise-chart.component.html',
    styleUrls: ['../../monitoring-bar-chart.scss'],
})
export class ExerciseChartComponent implements OnInit {
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
