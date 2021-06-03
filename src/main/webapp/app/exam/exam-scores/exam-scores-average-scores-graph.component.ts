import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ChartDataSets, ChartOptions, ChartType } from 'chart.js';
import { BaseChartDirective, Label } from 'ng2-charts';
import { TranslateService } from '@ngx-translate/core';
import { GraphColors } from 'app/entities/statistics.model';
import { AggregatedExerciseGroupResult } from 'app/exam/exam-scores/exam-score-dtos.model';

@Component({
    selector: 'jhi-exam-scores-average-scores-graph',
    templateUrl: './exam-scores-average-scores-graph.component.html',
})
export class ExamScoresAverageScoresGraphComponent implements OnInit {
    @Input() averageScores: AggregatedExerciseGroupResult;
    @Input() legendPosition: string | undefined;
    @Input() standardDeviation: number[] = [10];

    height = 25;

    // Histogram related properties
    barChartOptions: ChartOptions = {};
    barChartType: ChartType = 'horizontalBar';
    averagePointsTooltip: string;
    chartLegend = false;

    // Data
    barChartLabels: Label[] = [];
    chartData: ChartDataSets[] = [];
    absolutePoints: (number | undefined)[] = [];

    @ViewChild(BaseChartDirective) chart: BaseChartDirective;

    constructor(private service: StatisticsService, private translateService: TranslateService) {}

    ngOnInit(): void {
        this.averagePointsTooltip = this.translateService.instant('artemisApp.examScores.averagePointsTooltip');
        this.initializeChart();
        this.createCharts();
    }

    private initializeChart(): void {
        const colors = [GraphColors.BLUE];
        const labels = [this.averageScores.title];
        const absoluteData = [this.averageScores.averagePoints!];
        const relativeData: number[] = [this.averageScores.averagePercentage! - this.averageScores.standardDeviation!];
        this.averageScores.exerciseResults.forEach((exercise) => {
            labels.push(exercise.title);
            colors.push(GraphColors.DARK_BLUE);
            absoluteData.push(exercise.averagePoints!);
            relativeData.push(exercise.averagePercentage! - exercise.standardDeviation!);
            this.height += 25;
            this.standardDeviation.push(10);
        });
        this.barChartLabels = labels;
        this.absolutePoints = absoluteData;

        this.chartData = [
            {
                data: relativeData,
                backgroundColor: colors,
                borderColor: colors,
                hoverBackgroundColor: colors,
                barPercentage: 0.75,
            },
            {
                data: this.standardDeviation,
                backgroundColor: 'rgba(219, 53, 69, 0.3)',
                borderColor: 'rgba(219, 53, 69, 0.3)',
                hoverBackgroundColor: 'rgba(219, 53, 69, 0.3)',
                barPercentage: 0.75,
            },
            {
                data: this.standardDeviation,
                backgroundColor: 'rgba(40, 167, 69, 0.3)',
                borderColor: 'rgba(40, 167, 69, 0.3)',
                hoverBackgroundColor: 'rgba(40, 167, 69, 0.3)',
                barPercentage: 0.75,
            },
        ];
        console.log();
    }

    private createCharts() {
        const self = this;
        this.barChartOptions = {
            title: {
                display: true,
                text: self.averageScores.title,
            },
            responsive: true,
            hover: {
                animationDuration: 0,
            },
            animation: {
                duration: 1,
            },
            scales: {
                xAxes: [
                    {
                        stacked: true,
                        gridLines: {
                            display: true,
                        },
                        ticks: {
                            display: !!self.legendPosition,
                            beginAtZero: true,
                            min: 0,
                            max: 100,
                            stepSize: 10,
                            callback(value: number) {
                                return value + '%';
                            },
                        },
                    },
                ],
                yAxes: [
                    {
                        stacked: true,
                        gridLines: {
                            display: true,
                        },
                        ticks: {
                            padding: 15,
                            autoSkip: false,
                            fontStyle: 'bold',
                            callback() {
                                return '';
                            },
                        },
                    },
                ],
            },
            tooltips: {
                mode: 'index',
                enabled: true,
                callbacks: {
                    label(tooltipItem: any) {
                        if (!self.absolutePoints && !self.chartData[0].data) {
                            return ' 0';
                        }
                        return `${self.averagePointsTooltip}: ${self.absolutePoints[tooltipItem.index]} (${self.chartData[0].data![tooltipItem.index]}%)`;
                    },
                },
            },
        };
    }
}
