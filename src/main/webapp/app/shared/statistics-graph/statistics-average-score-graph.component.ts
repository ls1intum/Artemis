import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ChartDataSets, ChartOptions, ChartType } from 'chart.js';
import { BaseChartDirective, Label } from 'ng2-charts';
import { DataSet } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';
import { TranslateService } from '@ngx-translate/core';
import { Graphs, SpanType } from 'app/entities/statistics.model';
import { CourseManagementStatisticsModel } from 'app/entities/quiz/course-management-statistics-model';

@Component({
    selector: 'jhi-statistics-average-score-graph',
    templateUrl: './statistics-average-score-graph.component.html',
})
export class StatisticsAverageScoreGraphComponent implements OnInit {
    @Input()
    graphType: Graphs;
    @Input()
    exerciseAverageScores: CourseManagementStatisticsModel[];
    @Input()
    courseAverage: number;

    exerciseTitles: string[];
    averagePoints: number[] = [];

    // Html properties
    LEFT = false;
    RIGHT = true;
    SpanType = SpanType;
    Graphs = Graphs;

    // Histogram related properties
    barChartOptions: ChartOptions = {};
    barChartType: ChartType = 'bar';
    lineChartType: ChartType = 'line';
    amountOfStudents: string;
    courseAverageLegend: string;
    chartName: string;
    barChartLegend = true;
    chartTime: any;
    // Data
    barChartLabels: Label[] = [];
    chartData: ChartDataSets[] = [];
    dataForSpanType: number[];

    // Left arrow -> decrease, right arrow -> increase
    currentPeriod = 0;

    @ViewChild(BaseChartDirective) chart: BaseChartDirective;

    constructor(private service: StatisticsService, private translateService: TranslateService) {}

    /**
     * Life cycle hook to indicate component changes
     */
    ngOnInit(): void {
        this.chartName = this.translateService.instant(`artemisApp.course.averageScore`);
        this.amountOfStudents = this.translateService.instant('artemisApp.courseStatistics.amountOfStudents');
        this.courseAverageLegend = this.translateService.instant('artemisApp.courseStatistics.courseAverage');
        this.initializeChart();
        this.createCharts();
    }

    private initializeChart(): void {
        this.barChartLabels = this.exerciseAverageScores.slice(this.currentPeriod * 10, (this.currentPeriod + 1) * 10).map((exercise) => exercise.exerciseName);
        this.chartData = [
            {
                label: this.courseAverageLegend,
                data: new Array(this.barChartLabels.length).fill(this.courseAverage),
                backgroundColor: 'rgba(0, 0, 0, 0)',
                type: 'line',
                pointRadius: 0,
                borderColor: 'rgba(93,138,201,1)',
                hoverBackgroundColor: 'rgba(93,138,201,1)',
                hoverBorderColor: 'rgba(93,138,201,1)',
            },
            {
                label: this.amountOfStudents,
                data: this.exerciseAverageScores.slice(this.currentPeriod * 10, (this.currentPeriod + 1) * 10).map((exercise) => exercise.averageScore),
                backgroundColor: 'rgba(53,61,71,1)',
                borderColor: 'rgba(53,61,71,1)',
                hoverBackgroundColor: 'rgba(53,61,71,1)',
            },
        ];
    }

    private createCharts() {
        this.barChartOptions = {
            layout: {
                padding: {
                    top: 20,
                },
            },
            responsive: true,
            hover: {
                animationDuration: 0,
                intersect: true,
            },
            animation: {
                duration: 1,
                onComplete() {
                    const chartInstance = this.chart,
                        ctx = chartInstance.ctx;
                    ctx.textAlign = 'center';
                    ctx.textBaseline = 'bottom';

                    this.data.datasets.forEach(function (dataset: DataSet, j: number) {
                        const meta = chartInstance.controller.getDatasetMeta(j);
                        meta.data.forEach(function (bar: any, index: number) {
                            const data = dataset.data[index];
                            ctx.fillText(String(data), bar._model.x, bar._model.y - 5);
                        });
                    });
                },
            },
            scales: {
                yAxes: [
                    {
                        ticks: {
                            beginAtZero: true,
                            min: 0,
                            max: 100,
                        },
                    },
                ],
                xAxes: [
                    {
                        ticks: {
                            padding: 0,
                        },
                    },
                ],
            },
        };
    }

    public switchTimeSpan(index: boolean): void {
        // eslint-disable-next-line chai-friendly/no-unused-expressions
        index ? (this.currentPeriod += 1) : (this.currentPeriod -= 1);
        this.initializeChart();
    }
}
