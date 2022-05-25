import { Component, OnInit, Input } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { Exam } from 'app/entities/exam.model';
import { ChartData, getColor } from 'app/exam/monitoring/charts/monitoring-chart';

@Component({
    selector: 'jhi-exercise-group-chart',
    templateUrl: './exercise-group-chart.component.html',
    styleUrls: ['../../monitoring-chart.scss'],
})
export class ExerciseGroupChartComponent implements OnInit {
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
            let amount = 0;
            group.exercises!.forEach((exercise) => {
                // TODO: Replace with real data
                amount += Math.floor(Math.random() * 100);
            });
            this.ngxData.push(new ChartData(group.title ?? '', amount));
            this.ngxColor.domain.push(getColor(index));
        });
    }
}
