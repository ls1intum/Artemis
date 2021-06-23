import { AfterViewInit, Component, ElementRef, Input, OnDestroy, ViewChild } from '@angular/core';
import Chart from 'chart.js/auto';
import { ChartDataset, ChartOptions } from 'chart.js';
import { BaseChartDirective, Color, Label } from 'ng2-charts';
import { ExerciseScoresChartService, ExerciseScoresDTO } from 'app/overview/visualizations/exercise-scores-chart.service';
import { JhiAlertService } from 'ng-jhipster';
import { onError } from 'app/shared/util/global.utils';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import * as _ from 'lodash';
import { TranslateService } from '@ngx-translate/core';
import { ExerciseType } from 'app/entities/exercise.model';

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
export class ExerciseScoresChartComponent implements AfterViewInit {
    @Input()
    courseId: number;
    isLoading = false;
    public exerciseScores: ExerciseScoresDTO[] = [];

    @ViewChild(BaseChartDirective)
    chartDirective: BaseChartDirective;
    @ViewChild('chartDiv')
    chartDiv: ElementRef;

    constructor(
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private alertService: JhiAlertService,
        private exerciseScoresChartService: ExerciseScoresChartService,
        private translateService: TranslateService,
    ) {}

    ngAfterViewInit() {
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
                    // we show all the exercises ordered by their release data
                    this.exerciseScores = _.sortBy(exerciseScoresResponse.body!, (exerciseScore) => exerciseScore.releaseDate);
                    this.initializeChart();
                    this.defineOptions();
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
        this.addData();
    }

    private addData() {
        for (const exerciseScoreDTO of this.exerciseScores) {
            this.labels!.push(exerciseScoreDTO.exerciseTitle!);
            this.dataSets[0]!.data.push(exerciseScoreDTO.scoreOfStudent!);
            this.dataSets[1]!.data.push(exerciseScoreDTO.averageScoreAchieved!);
            this.dataSets[2]!.data.push(exerciseScoreDTO.maxScoreAchieved!);
        }
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

    public dataSets: ChartDataset[] = [
        // score of logged in user in exercise
        {
            fill: false,
            data: [],
            label: this.translateService.instant('artemisApp.exercise-scores-chart.yourScoreLabel'),
            pointStyle: 'circle',
            borderWidth: 3,
            tension: 0,
            spanGaps: true,
        },
        // average score in exercise
        {
            fill: false,
            data: [],
            label: this.translateService.instant('artemisApp.exercise-scores-chart.averageScoreLabel'),
            pointStyle: 'rect',
            borderWidth: 3,
            tension: 0,
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
            tension: 0,
            spanGaps: true,
            borderDash: [15, 3, 3, 3],
        },
    ];
    public labels: Label[] = this.exerciseScores.map((exerciseScoreDTO) => exerciseScoreDTO.exerciseTitle!);
    public chartOptions: ChartOptions = {};

    private defineOptions() {
        const self = this;
        this.chartOptions = {
            // we show a pointer to indicate to the user that a data point is clickable (navigation to exercise)
            onHover: (event: any, chartElement) => {
                event.native.target.style.cursor = chartElement[0] ? 'pointer' : 'default';
            },
            // when the user clicks on a data point, we navigate to the subpage of the corresponding exercise
            onClick: (event: any, context: any) => {
                const index = context[0].index;
                if (index) {
                    const exerciseId = self.exerciseScores[index].exerciseId;
                    if (exerciseId) {
                        this.navigateToExercise(exerciseId!);
                    }
                }
            },
            plugins: {
                tooltip: {
                    callbacks: {
                        label(context) {
                            let label = context.dataset.label || '';

                            if (label) {
                                label += ': ';
                            }
                            label += Math.round((context.parsed.y as number) * 100) / 100;
                            label += ' %';
                            return label;
                        },
                        footer(context) {
                            const index = context[0].dataIndex;
                            const exerciseType = self.exerciseScores[index].exerciseType;
                            return [`Exercise Type: ${exerciseType}`];
                        },
                    },
                },
                title: {
                    display: false,
                },
                legend: {
                    position: 'left',
                },
            },
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: {
                    title: {
                        display: true,
                        text: this.translateService.instant('artemisApp.exercise-scores-chart.yAxis'),
                        font: {
                            size: 12,
                        },
                    },
                    suggestedMax: 100,
                    suggestedMin: 0,
                    beginAtZero: true,
                    ticks: {
                        precision: 0,
                        font: {
                            size: 12,
                        },
                    },
                },
                x: {
                    title: {
                        display: true,
                        text: this.translateService.instant('artemisApp.exercise-scores-chart.xAxis'),
                        font: {
                            size: 12,
                        },
                    },
                    ticks: {
                        autoSkip: false,
                        font: {
                            size: 12,
                        },
                        callback(index: number) {
                            const label = self.labels[index] + '';
                            if (label.length > 20) {
                                // shorten exercise title if too long (will be displayed in full in tooltip)
                                return label.substr(0, 20) + '...';
                            } else {
                                return label;
                            }
                        },
                    },
                },
            },
        };
    }

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
