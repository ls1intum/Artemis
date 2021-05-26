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
    @Input() averageScores: AggregatedExerciseGroupResult[];

    // Histogram related properties
    barChartOptions: ChartOptions = {};
    barChartType: ChartType = 'horizontalBar';
    exerciseAverageScoreLegend: string;
    exerciseGroupAverageScoreLegend: string;
    chartLegend = true;

    // Data
    barChartLabels: Label[] = [];
    chartData: ChartDataSets[] = [];
    absolutePoints: (number | undefined)[][] = [];

    @ViewChild(BaseChartDirective) chart: BaseChartDirective;

    constructor(private service: StatisticsService, private translateService: TranslateService) {}

    ngOnInit(): void {
        this.exerciseAverageScoreLegend = this.translateService.instant('artemisApp.examScores.averageScoreGraphTitle');
        this.exerciseGroupAverageScoreLegend = this.translateService.instant('artemisApp.examScores.exerciseGroupAverageTitle');
        this.initializeChart();
        this.createCharts();
    }

    private initializeChart(): void {
        /*this.barChartLabels = this.averageScores.map((exerciseGroup) => {
            const titles = exerciseGroup.exerciseResults.map((exercise) => exercise.title);
            titles.unshift(exerciseGroup.title);
            return titles;
        });*/
        this.barChartLabels = this.averageScores.map((exerciseGroup) => exerciseGroup.title);

        const exerciseGroupScores = this.averageScores.map((exerciseGroup) => {
            const percentages = exerciseGroup.exerciseResults.map((exercise) => exercise.averagePercentage);
            percentages.unshift(exerciseGroup.averagePercentage);
            // return percentages.map((score) => score != undefined ? score : 0);
            return percentages.filter((score) => score != undefined);
        });
        this.absolutePoints = this.averageScores.map((exerciseGroup) => {
            const percentages = exerciseGroup.exerciseResults.map((exercise) => exercise.averagePoints);
            percentages.unshift(exerciseGroup.averagePoints);
            // return percentages.map((score) => score != undefined ? score : 0);
            return percentages.filter((score) => score != undefined);
        });
        // Prepare dataObjects
        const length = this.calculateMaxLength(exerciseGroupScores);
        // Average exerciseGroup score bar
        this.chartData.push({
            label: this.exerciseGroupAverageScoreLegend,
            data: [],
            backgroundColor: GraphColors.BLUE,
            borderColor: GraphColors.BLUE,
            hoverBackgroundColor: GraphColors.BLUE,
        });
        this.chartData.push({
            label: this.exerciseAverageScoreLegend,
            data: [],
            backgroundColor: GraphColors.DARK_BLUE,
            borderColor: GraphColors.DARK_BLUE,
            hoverBackgroundColor: GraphColors.DARK_BLUE,
        });
        // Average exercise score bars
        for (let i = 2; i < length; i++) {
            const dataObject = {
                data: [],
                backgroundColor: GraphColors.DARK_BLUE,
                borderColor: GraphColors.DARK_BLUE,
                hoverBackgroundColor: GraphColors.DARK_BLUE,
            };
            this.chartData.push(dataObject);
        }

        for (let i = 0; i < exerciseGroupScores.length; i++) {
            for (let j = 0; j < exerciseGroupScores[i].length; j++) {
                // @ts-ignore
                this.chartData[j]['data'][i] = exerciseGroupScores[i][j];
            }
        }
        /*this.chartData.map((dataSet) => {
            // @ts-ignore
            dataSet.data = dataSet.data!.filter((dataElement: number) => dataElement != undefined);
        });*/
        console.log(this.chartData);
    }

    private createCharts() {
        const self = this;
        this.barChartOptions = {
            legend: {
                position: 'bottom',
            },
            responsive: true,
            hover: {
                animationDuration: 0,
            },
            animation: {
                duration: 1,
                /*onComplete() {
                    const chartInstance = this.chart,
                        ctx = chartInstance.ctx;
                    this.data.datasets.forEach(function (dataset: DataSet, j: number) {
                        const meta = chartInstance.controller.getDatasetMeta(j);
                        meta.data.forEach(function (bar: any, index: number) {
                            const label = bar._model.label;
                            const xOffset = 10;
                            const yOffset = bar._model.y - 42;
                            ctx.fillText(label, xOffset, yOffset);
                        });
                    });
                }*/
                /*onComplete() {
                    const chartInstance = this.chart,
                        ctx = chartInstance.ctx;
                    ctx.textAlign = 'center';
                    ctx.textBaseline = 'bottom';

                    this.data.datasets.forEach(function (dataset: DataSet, j: number) {
                        const meta = chartInstance.controller.getDatasetMeta(j);
                        meta.data.forEach(function (bar: any, index: number) {
                            const label = bar._model.label;
                            // const xOffset = 10;
                            // const yOffset = bar._model.y - 42;
                            ctx.fillText(label, bar._model.x, bar._model.y);
                        });
                    });
                },*/
            },
            scales: {
                xAxes: [
                    {
                        ticks: {
                            beginAtZero: true,
                            min: 0,
                            max: 100,
                            stepSize: 10,
                            callback(value: number) {
                                return value + '%';
                            },
                        },
                        scaleLabel: {
                            fontStyle: 'bold',
                        },
                    },
                ],
                yAxes: [
                    {
                        ticks: {
                            backdropPaddingX: 100,
                            autoSkip: false,
                            fontStyle: 'bold',
                            callback(title: string) {
                                return title.length > 30 ? title.substr(0, 10) + '...' : title;
                            },
                        },
                    },
                ],
            },
            tooltips: {
                enabled: true,
                /*callbacks: {
                    label(tooltipItem: any) {
                        const points = self.translateService.instant('artemisApp.exam.points');
                        if (!self.absolutePoints) {
                            return ' 0 ' + points;
                        }

                        return ' ' + self.absolutePoints[tooltipItem.index] + ' ' + points;
                    },
                },*/
            },
        };
    }

    private calculateMaxLength(array: (number | undefined)[][]) {
        let maxLength = 0;
        array.forEach((subArray) => {
            if (subArray.length > maxLength) {
                maxLength = subArray.length;
            }
        });
        return maxLength;
    }
}
