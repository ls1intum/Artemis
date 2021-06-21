import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ChartDataset, ChartOptions, ChartType } from 'chart.js';
import { BaseChartDirective, Label } from 'ng2-charts';
import { DataSet } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';
import { TranslateService } from '@ngx-translate/core';
import { GraphColors } from 'app/entities/statistics.model';
import { round } from 'app/shared/util/utils';
import Chart from 'chart.js/auto';

@Component({
    selector: 'jhi-statistics-score-distribution-graph',
    templateUrl: './statistics-score-distribution-graph.component.html',
})
export class StatisticsScoreDistributionGraphComponent implements OnInit {
    @Input()
    averageScoreOfExercise: number | undefined;
    @Input()
    scoreDistribution: number[] | undefined;
    @Input()
    numberOfExerciseScores: number | undefined;

    // Histogram related properties
    barChartOptions: ChartOptions = {};
    barChartType: ChartType = 'bar';
    exerciseAverageLegend: string;
    distributionLegend: string;
    chartLegend = true;

    // Data
    barChartLabels: Label[] = [];
    chartData: ChartDataset[] = [];
    relativeChartData: number[] = [];

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
        if (this.numberOfExerciseScores && this.numberOfExerciseScores > 0) {
            this.relativeChartData = [];
            for (const value of this.scoreDistribution!) {
                this.relativeChartData.push(round((value * 100) / this.numberOfExerciseScores));
            }
        } else {
            this.relativeChartData = new Array(10).fill(0);
        }
        this.chartData = [
            {
                // Average exercise score bars
                label: this.distributionLegend,
                data: this.relativeChartData,
                backgroundColor: GraphColors.DARK_BLUE,
                borderColor: GraphColors.DARK_BLUE,
                hoverBackgroundColor: GraphColors.DARK_BLUE,
                barPercentage: 1.0,
                categoryPercentage: 1.0,
            },
        ];
    }

    private createCharts() {
        const self = this;
        this.barChartOptions = {
            plugins: {
                legend: {
                    position: 'bottom',
                },
                datalabels: {
                    display: true,
                    anchor: 'end',
                    align: 'end',
                    offset: 0,
                },
                tooltip: {
                    enabled: true,
                    callbacks: {
                        label(tooltipItem: any) {
                            if (!self.scoreDistribution) {
                                return ' 0';
                            }

                            return ' ' + self.scoreDistribution[tooltipItem.dataIndex];
                        },
                    },
                },
            },
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
                        stepSize: 20,
                        callback(value: number) {
                            return value + '%';
                        },
                    },
                },
            },
        };
    }
}
