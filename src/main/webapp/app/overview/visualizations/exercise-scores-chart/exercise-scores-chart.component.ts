import { AfterViewInit, Component, ElementRef, Input, OnDestroy, ViewChild } from '@angular/core';
import * as Chart from 'chart.js';
import { ChartDataSets, ChartOptions } from 'chart.js';
import { BaseChartDirective, Color, Label } from 'ng2-charts';
import { ExerciseScoresChartService, ExerciseScoresDTO } from 'app/overview/visualizations/exercise-scores-chart.service';
import { AlertService } from 'app/core/util/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import * as _ from 'lodash-es';
import { TranslateService } from '@ngx-translate/core';
import { ExerciseType } from 'app/entities/exercise.model';
import { round } from 'app/shared/util/utils';

// this exercise information is needed for tooltip generation and to navigate to an exercise page
export class CustomChartPoint implements Chart.ChartPoint {
    y: number;
    exerciseId: number;
    exerciseTitle: string;
    exerciseType: ExerciseType;
}

@Component({
    selector: 'jhi-exercise-scores-chart',
    templateUrl: './exercise-scores-chart.component.html',
    styleUrls: ['./exercise-scores-chart.component.scss'],
})
export class ExerciseScoresChartComponent implements AfterViewInit, OnDestroy {
    @Input()
    courseId: number;
    isLoading = false;
    public exerciseScores: ExerciseScoresDTO[] = [];

    @ViewChild(BaseChartDirective)
    chartDirective: BaseChartDirective;
    chartInstance: Chart;
    @ViewChild('chartDiv')
    chartDiv: ElementRef;

    constructor(
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private alertService: AlertService,
        private exerciseScoresChartService: ExerciseScoresChartService,
        private translateService: TranslateService,
    ) {}

    ngOnDestroy() {
        // important to prevent memory leaks
        this.chartInstance.destroy();
    }

    ngAfterViewInit() {
        this.chartInstance = this.chartDirective.chart;
        this.activatedRoute.parent!.params.subscribe((params) => {
            this.courseId = +params['courseId'];
            if (this.courseId) {
                this.loadDataAndInitializeChart();
            }
        });
    }

    private loadDataAndInitializeChart() {
        this.isLoading = true;
        this.exerciseScoresChartService
            .getExerciseScoresForCourse(this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe(
                (exerciseScoresResponse) => {
                    this.exerciseScores = exerciseScoresResponse.body!;
                    this.initializeChart();
                },
                (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            );
    }

    private initializeChart() {
        // we calculate the chart width depending on the number of exercises we have to show. If you look into
        // exercise-scores-chart.component.scss you will see that we show a horizontal navigation bar when the
        // chart has reached a certain width
        let chartWidth = 80 * this.exerciseScores.length;

        if (chartWidth < 1200) {
            chartWidth = 1200;
        }

        this.chartDiv.nativeElement.setAttribute('style', `width: ${chartWidth}px;`);
        this.chartInstance.resize();
        // we show all the exercises ordered by their release data
        const sortedExerciseScores = _.sortBy(this.exerciseScores, (exerciseScore) => exerciseScore.releaseDate);
        this.addData(this.chartInstance, sortedExerciseScores);
    }

    private addData(chart: Chart, exerciseScoresDTOs: ExerciseScoresDTO[]) {
        for (const exerciseScoreDTO of exerciseScoresDTOs) {
            const extraInformation = {
                exerciseId: exerciseScoreDTO.exerciseId,
                exerciseTitle: exerciseScoreDTO.exerciseTitle,
                exerciseType: exerciseScoreDTO.exerciseType,
            };

            chart.data.labels!.push(exerciseScoreDTO.exerciseTitle!);
            // from each dto we generate a data point for each of three data sets
            (chart.data.datasets![0].data as CustomChartPoint[])!.push({
                y: exerciseScoreDTO.scoreOfStudent,
                ...extraInformation,
            } as CustomChartPoint);
            (chart.data.datasets![1].data as CustomChartPoint[])!.push({
                y: exerciseScoreDTO.averageScoreAchieved,
                ...extraInformation,
            } as CustomChartPoint);
            (chart.data.datasets![2].data as CustomChartPoint[])!.push({
                y: exerciseScoreDTO.maxScoreAchieved,
                ...extraInformation,
            } as CustomChartPoint);
        }
        this.chartInstance.update();
    }

    /**
     * We navigate to the exercise sub page when the user clicks on a data point
     */
    navigateToExercise(exerciseId: number) {
        this.router.navigate(['courses', this.courseId, 'exercises', exerciseId]);
    }

    /* ------------------------------ Settings for the Chart ------------------------------ */
    /**
     * For each exercise we show three data points, hence we need three data sets:
     * 1.) Score achieved by the user in the exercise
     * 2.) Average score achieved by all users in the exercise
     * 3.) Best score achieved by a user in the exercise
     */

    public dataSets: ChartDataSets[] = [
        // score of logged in user in exercise
        {
            fill: false,
            data: [],
            label: this.translateService.instant('artemisApp.exercise-scores-chart.yourScoreLabel'),
            pointStyle: 'circle',
            borderWidth: 3,
            lineTension: 0,
            spanGaps: true,
        },
        // average score in exercise
        {
            fill: false,
            data: [],
            label: this.translateService.instant('artemisApp.exercise-scores-chart.averageScoreLabel'),
            pointStyle: 'rect',
            borderWidth: 3,
            lineTension: 0,
            spanGaps: true,
            borderDash: [1, 1],
        },
        // best score in exercise
        {
            fill: false,
            data: [],
            label: this.translateService.instant('artemisApp.exercise-scores-chart.maximumScoreLabel'),
            pointStyle: 'triangle',
            borderWidth: 3,
            lineTension: 0,
            spanGaps: true,
            borderDash: [15, 3, 3, 3],
        },
    ];
    public labels: Label[] = this.exerciseScores.map((exerciseScoreDTO) => exerciseScoreDTO.exerciseTitle!);
    public chartOptions: ChartOptions = {
        // we show the pointer to indicate to the user that a data point is clickable (navigation to exercise)
        onHover: (event: any, chartElement) => {
            event.target.style.cursor = chartElement[0] ? 'pointer' : 'default';
        },
        // when the user clicks on a data point, we navigate to the subpage of the corresponding exercise
        onClick: (evt) => {
            const point: any = this.chartInstance.getElementAtEvent(evt)[0];

            if (point) {
                const value: any = this.chartInstance.data.datasets![point._datasetIndex]!.data![point._index];
                if (value.exerciseId) {
                    this.navigateToExercise(value.exerciseId);
                }
            }
        },
        tooltips: {
            callbacks: {
                label(tooltipItem, data) {
                    let label = data.datasets![tooltipItem.datasetIndex!].label || '';

                    if (label) {
                        label += ': ';
                    }
                    label += round(tooltipItem.yLabel as number, 2);
                    label += ' %';
                    return label;
                },
                footer(tooltipItem, data) {
                    const dataset = data.datasets![tooltipItem[0].datasetIndex!].data![tooltipItem[0].index!];
                    const exerciseType = (dataset as any).exerciseType;
                    return [`Exercise Type: ${exerciseType}`];
                },
            },
        },
        responsive: true,
        maintainAspectRatio: false,
        title: {
            display: false,
        },
        legend: {
            position: 'left',
        },
        scales: {
            yAxes: [
                {
                    scaleLabel: {
                        display: true,
                        labelString: this.translateService.instant('artemisApp.exercise-scores-chart.yAxis'),
                        fontSize: 12,
                    },
                    ticks: {
                        suggestedMax: 100,
                        suggestedMin: 0,
                        beginAtZero: true,
                        precision: 0,
                        fontSize: 12,
                    },
                },
            ],
            xAxes: [
                {
                    scaleLabel: {
                        display: true,
                        labelString: this.translateService.instant('artemisApp.exercise-scores-chart.xAxis'),
                        fontSize: 12,
                    },
                    ticks: {
                        autoSkip: false,
                        fontSize: 12,
                        callback(exerciseTitle: string) {
                            if (exerciseTitle.length > 20) {
                                // shorten exercise title if too long (will be displayed in full in tooltip)
                                return exerciseTitle.substr(0, 20) + '...';
                            } else {
                                return exerciseTitle;
                            }
                        },
                    },
                },
            ],
        },
    };
    public chartColors: Color[] = [
        // score of logged in user in exercise
        {
            borderColor: 'skyBlue',
            backgroundColor: 'skyBlue',
            hoverBackgroundColor: 'black',
            hoverBorderColor: 'black',
        },
        // average score in exercise
        {
            borderColor: 'salmon',
            backgroundColor: 'salmon',
            hoverBackgroundColor: 'black',
            hoverBorderColor: 'black',
        },
        // best score in exercise
        {
            borderColor: 'limeGreen',
            backgroundColor: 'limeGreen',
            hoverBackgroundColor: 'black',
            hoverBorderColor: 'black',
        },
    ];
}
