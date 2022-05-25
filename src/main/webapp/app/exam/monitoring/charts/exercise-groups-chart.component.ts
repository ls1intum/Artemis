import { Component, OnDestroy, OnInit, Input } from '@angular/core';
import { Color, ScaleType, LegendPosition } from '@swimlane/ngx-charts';
import { Exam } from '../../../entities/exam.model';

class ExerciseStatistic {
    name: string;
    value: number;

    constructor(name: string, value: number) {
        this.name = name;
        this.value = value;
    }
}

@Component({
    selector: 'jhi-exercise-groups-chart',
    templateUrl: './exercise-groups-chart.component.html',
    styleUrls: ['./exercise-groups-chart.component.scss'],
})
export class ExerciseGroupsChartComponent implements OnInit, OnDestroy {
    @Input()
    exam: Exam;

    // Chart
    ngxData: ExerciseStatistic[];
    colors = ['#fd7f6f', '#7eb0d5', '#b2e061', '#bd7ebe', '#ffb55a', '#ffee65', '#beb9db', '#fdcce5', '#8bd3c7'];
    ngxColor = {
        name: 'Amount of students per exercise grouped by exercise group',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    } as Color;

    showXAxis = true;
    showYAxis = true;
    gradient = false;
    showLegend = true;
    showXAxisLabel = true;
    xAxisLabel = 'Amount of Students';
    showYAxisLabel = true;
    yAxisLabel = 'Exercise groups';
    legendTitle = 'Exercises';
    legendPosition = LegendPosition.Right;

    constructor() {}

    ngOnInit(): void {
        this.ngxData = [];
        this.exam.exerciseGroups!.forEach((group) => {
            group.exercises!.forEach((exercise, exerciseIndex) => {
                this.ngxData.push(new ExerciseStatistic(exercise.shortName ?? '', Math.floor(Math.random() * 100)));
                this.ngxColor.domain.push(this.colors[exerciseIndex % this.colors.length]);
            });
        });
    }

    ngOnDestroy(): void {}
}
