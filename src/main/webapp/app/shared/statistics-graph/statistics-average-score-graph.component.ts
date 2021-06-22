import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ChartDataset, ChartOptions, ChartType } from 'chart.js';
import { BaseChartDirective, Label } from 'ng2-charts';
import { TranslateService } from '@ngx-translate/core';
import { GraphColors } from 'app/entities/statistics.model';
import { CourseManagementStatisticsModel } from 'app/entities/quiz/course-management-statistics-model';

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

    // Histogram related properties
    barChartOptions: ChartOptions = {};
    barChartType: ChartType = 'bar';
    lineChartType: ChartType = 'line';
    exerciseAverageLegend: string;
    courseAverageLegend: string;
    chartName: string;
    barChartLegend = true;

    // Data
    barChartLabels: Label[] = [];
    chartData: ChartDataset[] = [];

    // Left arrow -> decrease, right arrow -> increase
    currentPeriod = 0;

    @ViewChild(BaseChartDirective) chart: BaseChartDirective;

    constructor(private service: StatisticsService, private translateService: TranslateService) {}

    ngOnInit(): void {
        this.chartName = this.translateService.instant(`artemisApp.course.averageScore`);
        this.exerciseAverageLegend = this.translateService.instant('artemisApp.courseStatistics.exerciseAverage');
        this.courseAverageLegend = this.translateService.instant('artemisApp.courseStatistics.courseAverage');
        this.initializeChart();
        this.createCharts();
    }

    private initializeChart(): void {
        this.barChartLabels = this.exerciseAverageScores.slice(this.currentPeriod, 10 + this.currentPeriod).map((exercise) => exercise.exerciseName);
        this.chartData = [
            {
                // Average course score line
                label: this.courseAverageLegend,
                data: new Array(this.barChartLabels.length).fill(this.courseAverage),
                backgroundColor: GraphColors.BLUE,
                fill: false,
                pointBackgroundColor: GraphColors.BLUE,
                pointBorderColor: GraphColors.BLUE,
                pointHoverBackgroundColor: GraphColors.BLUE,
                pointHoverBorderColor: GraphColors.BLUE,
                borderColor: GraphColors.BLUE,
                hoverBackgroundColor: GraphColors.BLUE,
                hoverBorderColor: GraphColors.BLUE,
                order: 1,
            },
            {
                // Average exercise score bars
                label: this.exerciseAverageLegend,
                data: this.exerciseAverageScores.slice(this.currentPeriod, 10 + this.currentPeriod).map((exercise) => exercise.averageScore),
                type: 'bar',
                backgroundColor: GraphColors.DARK_BLUE,
                borderColor: GraphColors.DARK_BLUE,
                hoverBackgroundColor: GraphColors.DARK_BLUE,
                order: 2,
            },
        ];
    }

    private createCharts() {
        const self = this;
        this.barChartOptions = {
            layout: {
                padding: {
                    top: 20,
                },
            },
            responsive: true,
            animation: {
                duration: 1,
            },
            scales: {
                y: {
                    beginAtZero: true,
                    min: 0,
                    max: 100,
                    ticks: {
                        autoSkip: true,
                    },
                },
                x: {
                    grid: {
                        offset: false,
                    },
                    ticks: {
                        autoSkip: false,
                        callback(index: number) {
                            const label = self.barChartLabels[index] + '';
                            return label.length > 10 ? label.substr(0, 10) + '...' : label;
                        },
                    },
                },
            },
            plugins: {
                annotation: {
                    annotations: {
                        line1: {
                            type: 'line',
                            yMin: this.courseAverage,
                            yMax: this.courseAverage,
                            borderColor: GraphColors.BLUE,
                            borderWidth: 4,
                        },
                    },
                },
            },
        };
    }

    // handles arrow clicks and updates the exercises which are shown, forward is boolean since it is either forward or backward
    public switchTimeSpan(forward: boolean): void {
        // eslint-disable-next-line chai-friendly/no-unused-expressions
        this.currentPeriod += forward ? 1 : -1;
        this.initializeChart();
    }
}
