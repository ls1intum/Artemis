import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ChartDataSets, ChartOptions, ChartType } from 'chart.js';
import { BaseChartDirective, Label } from 'ng2-charts';
import { DataSet } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';
import { TranslateService } from '@ngx-translate/core';
import { GraphColors, Graphs, SpanType } from 'app/entities/statistics.model';

@Component({
    selector: 'jhi-statistics-score-distribution-graph',
    templateUrl: './statistics-score-distribution-graph.component.html',
})
export class StatisticsScoreDistributionGraphComponent implements OnInit {
    @Input()
    averageScoreOfExercise: number;
    @Input()
    scoreDistribution: number[];

    // Html properties
    LEFT = false;
    RIGHT = true;
    SpanType = SpanType;
    Graphs = Graphs;

    // Histogram related properties
    barChartOptions: ChartOptions = {};
    barChartType: ChartType = 'bar';
    lineChartType: ChartType = 'line';
    exerciseAverageLegend: string;
    distributionLegend: string;
    chartLegend = true;

    // Data
    barChartLabels: Label[] = [];
    chartData: ChartDataSets[] = [];
    dataForSpanType: number[];

    // Left arrow -> decrease, right arrow -> increase
    currentPeriod = 0;

    @ViewChild(BaseChartDirective) chart: BaseChartDirective;

    constructor(private service: StatisticsService, private translateService: TranslateService) {}

    ngOnInit(): void {
        this.distributionLegend = this.translateService.instant('statistics.score_distribution');
        this.exerciseAverageLegend = this.translateService.instant('artemisApp.courseStatistics.exerciseAverage');
        this.initializeChart();
        this.createCharts();
    }

    private initializeChart(): void {
        this.barChartLabels = ['[0, 10)', '[10, 20)', '[20, 30)', '[30, 40)', '[40, 50)', '[50, 60)', '[60, 70)', '[70, 80)', '[80, 90)', '[90, 100]'];
        this.chartData = [
            {
                // Average course score line
                label: this.exerciseAverageLegend,
                data: new Array(this.barChartLabels.length).fill(this.averageScoreOfExercise),
                backgroundColor: GraphColors.BLUE,
                fill: false,
                pointBackgroundColor: GraphColors.BLUE_TRANSPARENT,
                pointBorderColor: GraphColors.BLUE_TRANSPARENT,
                pointHoverBackgroundColor: GraphColors.BLUE,
                pointHoverBorderColor: GraphColors.BLUE_TRANSPARENT,
                borderColor: GraphColors.BLUE,
                hoverBackgroundColor: GraphColors.BLUE,
                hoverBorderColor: GraphColors.BLUE,
            },
            {
                // Average exercise score bars
                label: this.distributionLegend,
                data: this.scoreDistribution,
                fill: false,
                backgroundColor: GraphColors.DARK_BLUE,
                borderColor: GraphColors.DARK_BLUE,
                hoverBackgroundColor: GraphColors.DARK_BLUE,
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
                            stepSize: 20,
                        },
                    },
                ],
                xAxes: [
                    {
                        gridLines: {
                            offsetGridLines: false,
                        },
                        ticks: {
                            autoSkip: false,
                        },
                    },
                ],
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
