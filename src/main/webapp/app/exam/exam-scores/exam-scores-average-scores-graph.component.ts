import { AfterViewInit, Component, Input, OnInit, ViewChild } from '@angular/core';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ChartDataSets, ChartOptions, ChartType } from 'chart.js';
import { BaseChartDirective, Label } from 'ng2-charts';
import { TranslateService } from '@ngx-translate/core';
import { GraphColors } from 'app/entities/statistics.model';
import { AggregatedExerciseGroupResult } from 'app/exam/exam-scores/exam-score-dtos.model';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';
import { round } from 'app/shared/util/utils';
import { ActivatedRoute, Router } from '@angular/router';
import { ExerciseType } from 'app/entities/exercise.model';
import { navigateToExamExercise } from 'app/utils/navigation.utils';

const BAR_HEIGHT = 15;

@Component({
    selector: 'jhi-exam-scores-average-scores-graph',
    templateUrl: './exam-scores-average-scores-graph.component.html',
})
export class ExamScoresAverageScoresGraphComponent implements OnInit, AfterViewInit {
    @Input() averageScores: AggregatedExerciseGroupResult;

    height = BAR_HEIGHT;
    courseId: number;
    examId: number;
    exerciseIds: number[] = [];
    exerciseTypes: ExerciseType[] = [];

    // Histogram related properties
    @ViewChild(BaseChartDirective)
    chartDirective: BaseChartDirective;
    chartInstance: Chart;
    barChartOptions: ChartOptions = {};
    barChartType: ChartType = 'horizontalBar';
    averagePointsTooltip: string;
    chartLegend = false;

    // Data
    barChartLabels: Label[] = [];
    chartData: ChartDataSets[] = [];
    absolutePoints: (number | undefined)[] = [];

    @ViewChild(BaseChartDirective) chart: BaseChartDirective;

    constructor(
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private service: StatisticsService,
        private translateService: TranslateService,
        private localeConversionService: LocaleConversionService,
    ) {}

    ngOnInit(): void {
        this.averagePointsTooltip = this.translateService.instant('artemisApp.examScores.averagePointsTooltip');
        this.activatedRoute.params.subscribe((params) => {
            this.courseId = +params['courseId'];
            this.examId = +params['examId'];
        });
        this.initializeChart();
        this.createCharts();
    }

    ngAfterViewInit() {
        this.chartInstance = this.chartDirective.chart;
    }

    private initializeChart(): void {
        const colors = [GraphColors.BLUE];
        const labels = [this.averageScores.title];
        const absoluteData = [this.averageScores.averagePoints!];
        const relativeData: number[] = [this.averageScores.averagePercentage!];
        this.averageScores.exerciseResults.forEach((exercise) => {
            labels.push(exercise.exerciseId + ' ' + exercise.title);
            colors.push(GraphColors.DARK_BLUE);
            absoluteData.push(exercise.averagePoints!);
            relativeData.push(exercise.averagePercentage!);

            this.height += BAR_HEIGHT;
            this.exerciseIds.push(exercise.exerciseId);
            this.exerciseTypes.push(exercise.exerciseType);
        });
        this.barChartLabels = labels;
        this.absolutePoints = absoluteData;

        this.chartData = [
            {
                data: relativeData,
                backgroundColor: colors,
                borderColor: colors,
                hoverBackgroundColor: colors,
                barPercentage: 0.9,
            },
        ];
    }

    roundAndPerformLocalConversion(points: number | undefined, exp: number, fractions = 1) {
        return this.localeConversionService.toLocaleString(round(points, exp), fractions);
    }

    private createCharts() {
        const self = this;
        this.barChartOptions = {
            layout: {
                padding: {
                    left: 130,
                },
            },
            title: {
                display: true,
                text: `Average scores of exercise group "${self.averageScores.title}" in comparison`,
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
                            display: true,
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
                            callback(title: string) {
                                return title.length > 20 ? title.substr(0, 20) + '...' : title;
                            },
                            mirror: true,
                            padding: 130,
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
                            return ' -';
                        }
                        return `${self.averagePointsTooltip}: ${self.roundAndPerformLocalConversion(self.absolutePoints[tooltipItem.index], 2, 2)} (${round(
                            self.chartData[0].data![tooltipItem.index],
                            2,
                        )}%)`;
                    },
                },
            },
            // we show the pointer to indicate to the user that a data point is clickable (navigation to exercise)
            onHover: (event: any, chartElement) => {
                const element = chartElement[0];
                if (element) {
                    event.target.style.cursor = element['_index'] !== 0 ? 'pointer' : 'default';
                }
            },
            // when the user clicks on a data point, we navigate to the scores page of the corresponding exercise
            onClick: (evt) => {
                const point: any = this.chartInstance.getElementAtEvent(evt)[0];

                if (point) {
                    const exerciseId = this.exerciseIds[point._index - 1];
                    const exerciseType = this.exerciseTypes[point._index - 1];
                    if (exerciseId && exerciseType) {
                        this.navigateToExercise(exerciseId, exerciseType);
                    }
                }
            },
        };
    }

    /**
     * We navigate to the exercise scores page when the user clicks on a data point
     */
    navigateToExercise(exerciseId: number, exerciseType: ExerciseType) {
        navigateToExamExercise(this.router, this.courseId, this.examId, this.averageScores.exerciseGroupId, exerciseType, exerciseId, 'scores');
    }
}
