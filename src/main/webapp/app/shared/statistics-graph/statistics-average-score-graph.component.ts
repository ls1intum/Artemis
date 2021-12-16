import { Component, Input, OnInit } from '@angular/core';
import { GraphColors, Graphs, SpanType } from 'app/entities/statistics.model';
import { CourseManagementStatisticsModel } from 'app/entities/quiz/course-management-statistics-model';
import { faArrowLeft, faArrowRight } from '@fortawesome/free-solid-svg-icons';
import { Color, ScaleType } from '@swimlane/ngx-charts';

export const ngxColor = {
    name: 'Course statistics',
    selectable: true,
    group: ScaleType.Ordinal,
    domain: [GraphColors.DARK_BLUE],
} as Color;

@Component({
    selector: 'jhi-statistics-average-score-graph',
    templateUrl: './statistics-average-score-graph.component.html',
})
export class StatisticsAverageScoreGraphComponent implements OnInit {
    @Input()
    exerciseAverageScores: CourseManagementStatisticsModel[];
    @Input()
    courseAverage: number;

    // Html properties
    LEFT = false;
    RIGHT = true;
    SpanType = SpanType;
    Graphs = Graphs;

    // Data
    barChartLabels: string[] = [];
    // ngx
    ngxData: any[] = [];
    readonly ngxColor = ngxColor;

    // Left arrow -> decrease, right arrow -> increase
    currentPeriod = 0;

    // Icons
    faArrowLeft = faArrowLeft;
    faArrowRight = faArrowRight;

    ngOnInit(): void {
        this.initializeChart();
    }

    private initializeChart(): void {
        this.barChartLabels = this.exerciseAverageScores.slice(this.currentPeriod, 10 + this.currentPeriod).map((exercise) => exercise.exerciseName);
        const data = this.exerciseAverageScores.slice(this.currentPeriod, 10 + this.currentPeriod).map((exercise) => exercise.averageScore);
        this.ngxData = [];
        data.forEach((exerciseScore, index) => {
            this.ngxData.push({ name: this.barChartLabels[index], value: exerciseScore });
        });
        this.ngxData = [...this.ngxData];
    }

    // handles arrow clicks and updates the exercises which are shown, forward is boolean since it is either forward or backward
    public switchTimeSpan(forward: boolean): void {
        // eslint-disable-next-line chai-friendly/no-unused-expressions
        this.currentPeriod += forward ? 1 : -1;
        this.initializeChart();
    }
}
