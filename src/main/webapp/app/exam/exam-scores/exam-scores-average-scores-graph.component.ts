import { Component, Input, OnInit, ViewChild } from '@angular/core';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ChartDataset, ChartOptions, ChartType } from 'chart.js';
import { BaseChartDirective, Label } from 'ng2-charts';
import { TranslateService } from '@ngx-translate/core';
import { GraphColors } from 'app/entities/statistics.model';
import { AggregatedExerciseGroupResult } from 'app/exam/exam-scores/exam-score-dtos.model';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';
import { round } from 'app/shared/util/utils';
import Chart from 'chart.js/auto';
import ChartDataLabels from 'chartjs-plugin-datalabels';

Chart.register(ChartDataLabels);
Chart.defaults.plugins.datalabels!.display = false;

const BAR_HEIGHT = 25;

@Component({
    selector: 'jhi-exam-scores-average-scores-graph',
    templateUrl: './exam-scores-average-scores-graph.component.html',
})
export class ExamScoresAverageScoresGraphComponent implements OnInit {
    @Input() averageScores: AggregatedExerciseGroupResult;

    height = BAR_HEIGHT;

    // Histogram related properties
    barChartOptions: ChartOptions = {};
    barChartType: ChartType = 'bar';
    averagePointsTooltip: string;
    chartLegend = false;

    // Data
    barChartLabels: Label[] = [];
    chartData: ChartDataset[] = [];
    absolutePoints: (number | undefined)[] = [];

    @ViewChild(BaseChartDirective) chart: BaseChartDirective;

    constructor(private service: StatisticsService, private translateService: TranslateService, private localeConversionService: LocaleConversionService) {}

    ngOnInit(): void {
        this.averagePointsTooltip = this.translateService.instant('artemisApp.examScores.averagePointsTooltip');
        this.initializeChart();
        this.createCharts();
    }

    private initializeChart(): void {
        const colors = [GraphColors.BLUE];
        const labels = [this.averageScores.title];
        const absoluteData = [this.averageScores.averagePoints!];
        const relativeData: number[] = [this.averageScores.averagePercentage!];
        this.averageScores.exerciseResults.forEach((exercise) => {
            labels.push(exercise.title);
            colors.push(GraphColors.DARK_BLUE);
            absoluteData.push(exercise.averagePoints!);
            relativeData.push(exercise.averagePercentage!);
            this.height += BAR_HEIGHT;
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
        ];
    }

    roundAndPerformLocalConversion(points: number | undefined, exp: number, fractions = 1) {
        return this.localeConversionService.toLocaleString(round(points, exp), fractions);
    }

    private createCharts() {
        const self = this;
        this.barChartOptions = {
            indexAxis: 'y',
            layout: {
                padding: {
                    left: 130,
                },
            },
            plugins: {
                legend: {
                    display: false,
                },
                title: {
                    display: true,
                    text: self.averageScores.title,
                },
                tooltip: {
                    mode: 'index',
                    enabled: true,
                    callbacks: {
                        label(tooltipItem: any) {
                            if (!self.absolutePoints && !self.chartData[0].data) {
                                return ' -';
                            }
                            return `${self.averagePointsTooltip}: ${self.roundAndPerformLocalConversion(self.absolutePoints[tooltipItem.dataIndex], 2, 2)} (${round(
                                self.chartData[0].data![tooltipItem.dataIndex],
                                2,
                            )}%)`;
                        },
                    },
                },
            },
            responsive: true,
            animation: {
                duration: 1,
            },
            scales: {
                x: {
                    grid: {
                        display: true,
                    },
                    beginAtZero: true,
                    min: 0,
                    max: 100,
                    ticks: {
                        display: true,
                        stepSize: 10,
                        callback(value: number) {
                            return value + '%';
                        },
                    },
                },
                y: {
                    grid: {
                        display: true,
                    },
                    ticks: {
                        mirror: true,
                        padding: -130,
                        callback(index: number) {
                            const label = self.barChartLabels[index] + '';
                            return label.length > 20 ? label.substr(0, 20) + '...' : label;
                        },
                    },
                },
            },
        };
    }
}
