import { Component, OnDestroy, OnInit, Input } from '@angular/core';
import { Color, ScaleType, LegendPosition } from '@swimlane/ngx-charts';
import { Exam } from 'app/entities/exam.model';
import { ChartData } from 'app/exam/monitoring/charts/chart';
import { GraphColors } from 'app/entities/statistics.model';

@Component({
    selector: 'jhi-exercise-chart',
    templateUrl: './exercise-chart.component.html',
    styleUrls: ['../bar-chart.scss'],
})
export class ExerciseChartComponent implements OnInit, OnDestroy {
    @Input()
    exam: Exam;

    // Chart
    ngxData: ChartData[] = [];
    colors = [
        GraphColors.MONITORING_VARIANT_ONE,
        GraphColors.MONITORING_VARIANT_TWO,
        GraphColors.MONITORING_VARIANT_THREE,
        GraphColors.MONITORING_VARIANT_FOUR,
        GraphColors.MONITORING_VARIANT_FIVE,
        GraphColors.MONITORING_VARIANT_SIX,
        GraphColors.MONITORING_VARIANT_SEVEN,
        GraphColors.MONITORING_VARIANT_EIGHT,
        GraphColors.MONITORING_VARIANT_NINE,
    ];
    ngxColor = {
        name: 'Amount of students per exercise grouped by exercise group',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    } as Color;
    xAxisLabel = 'Amount of Students';
    yAxisLabel = 'Exercises';
    legendTitle = 'Exercises';
    legendPosition = LegendPosition.Right;

    constructor() {}

    ngOnInit(): void {
        this.initData();
    }

    initData() {
        this.exam.exerciseGroups!.forEach((group, index) => {
            group.exercises!.forEach((exercise) => {
                this.ngxData.push(new ChartData(exercise.title ?? '', Math.floor(Math.random() * 100)));
                this.ngxColor.domain.push(this.colors[index % this.colors.length]);
            });
        });
    }

    ngOnDestroy(): void {}
}
