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

    height = 13;

    // Histogram related properties
    barChartOptions: ChartOptions = {};
    barChartType: ChartType = 'horizontalBar';
    exerciseAverageScoreLegend: string;
    exerciseGroupAverageScoreLegend: string;
    chartLegend = false;

    // Data
    barChartLabels: Label[] = [];
    chartData: ChartDataSets[] = [];
    absolutePoints: (number | undefined)[] = [];

    @ViewChild(BaseChartDirective) chart: BaseChartDirective;

    constructor(private service: StatisticsService, private translateService: TranslateService) {}

    ngOnInit(): void {
        this.exerciseAverageScoreLegend = this.translateService.instant('artemisApp.examScores.averageScoreGraphTitle');
        this.exerciseGroupAverageScoreLegend = this.translateService.instant('artemisApp.examScores.exerciseGroupAverageTitle');
        this.initializeChart();
        this.createCharts();
    }

    private initializeChart(): void {
        /*        const filteredAverageScores = this.averageScores.map((exerciseGroup) => {
            exerciseGroup.exerciseResults = exerciseGroup.exerciseResults.filter((exercise) => exercise.averagePercentage);
            return exerciseGroup;
        });*/

        const colors = [GraphColors.BLUE];
        const labels = [this.averageScores.title];
        const absoluteData = [this.averageScores.averagePoints];
        const relativeData = [this.averageScores.averagePercentage];
        this.averageScores.exerciseResults.forEach((exercise) => {
            labels.push(exercise.title);
            colors.push(GraphColors.DARK_BLUE);
            absoluteData.push(exercise.averagePoints);
            relativeData.push(exercise.averagePercentage);
            this.height += 13;
        });
        this.barChartLabels = labels;
        this.absolutePoints = absoluteData;

        // settings for first ar with different color
        this.chartData = [
            {
                label: this.exerciseGroupAverageScoreLegend,
                data: relativeData,
                backgroundColor: colors,
                borderColor: colors,
                hoverBackgroundColor: colors,
                barPercentage: 0.75,
            },
        ];
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
                enabled: true,
            },
        };
    }
}
