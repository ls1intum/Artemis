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
    chartLegend = false;
    labels = [0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100];

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
        const filteredAverageScores = this.averageScores.map((exerciseGroup) => {
            exerciseGroup.exerciseResults = exerciseGroup.exerciseResults.filter((exercise) => exercise.averagePercentage);
            return exerciseGroup;
        });
        this.barChartLabels = filteredAverageScores.map((exerciseGroup) => exerciseGroup.title);

        const exerciseGroupScores = filteredAverageScores.map((exerciseGroup) => {
            const exercisePercentages = exerciseGroup.exerciseResults.map((exercise) => exercise.averagePercentage);
            exercisePercentages.unshift(exerciseGroup.averagePercentage);
            return exercisePercentages.filter((score) => score != undefined);
        });
        this.absolutePoints = filteredAverageScores.map((exerciseGroup) => {
            const percentages = exerciseGroup.exerciseResults.map((exercise) => exercise.averagePoints);
            percentages.unshift(exerciseGroup.averagePoints);
            return percentages.filter((score) => score != undefined);
        });

        // Prepare dataObjects
        const length = this.calculateMaxLength(exerciseGroupScores);

        // settings for first ar with different color
        this.chartData.push({
            label: this.exerciseGroupAverageScoreLegend,
            data: [],
            backgroundColor: GraphColors.BLUE,
            borderColor: GraphColors.BLUE,
            hoverBackgroundColor: GraphColors.BLUE,
        });
        // settings for second bar with specific legend
        this.chartData.push({
            label: this.exerciseAverageScoreLegend + '1',
            data: [],
            backgroundColor: GraphColors.DARK_BLUE,
            borderColor: GraphColors.DARK_BLUE,
            hoverBackgroundColor: GraphColors.DARK_BLUE,
        });

        // add settings for rest of the bars
        for (let i = 2; i < length; i++) {
            const dataObject = {
                label: this.exerciseAverageScoreLegend + '' + i,
                data: [],
                backgroundColor: GraphColors.DARK_BLUE,
                borderColor: GraphColors.DARK_BLUE,
                hoverBackgroundColor: GraphColors.DARK_BLUE,
            };
            this.chartData.push(dataObject);
        }
        // add the actual data for each bar
        for (let i = 0; i < exerciseGroupScores.length; i++) {
            for (let j = 0; j < exerciseGroupScores[i].length; j++) {
                // @ts-ignore
                this.chartData[j]['data'][i] = exerciseGroupScores[i][j];
            }
        }
    }

    private createCharts() {
        this.barChartOptions = {
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
                        position: 'top',
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
