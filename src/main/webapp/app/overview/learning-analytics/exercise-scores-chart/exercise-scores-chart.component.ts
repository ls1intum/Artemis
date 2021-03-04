import { AfterViewInit, Component, ElementRef, Input, ViewChild } from '@angular/core';
import * as Chart from 'chart.js';
import { ChartDataSets, ChartOptions, ChartPoint, ChartType } from 'chart.js';
import { BaseChartDirective, Color, Label } from 'ng2-charts';
import { ExerciseScoresDTO, LearningAnalyticsService } from 'app/overview/learning-analytics/learning-analytics.service';
import { JhiAlertService } from 'ng-jhipster';
import { onError } from 'app/shared/util/global.utils';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import * as _ from 'lodash';
import { TranslateService } from '@ngx-translate/core';

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
    chartInstance: Chart;
    @ViewChild('chartDiv')
    chartDiv: ElementRef;
    public lineChartData: ChartDataSets[] = [
        {
            fill: false,
            data: [],
            label: this.translateService.instant('artemisApp.exercise-scores-chart.yourScoreLabel'),
            pointStyle: 'circle',
            borderWidth: 3,
            lineTension: 0,
            spanGaps: true,
        },
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
    public lineChartLabels: Label[] = this.exerciseScores.map((exerciseScoreDTO) => exerciseScoreDTO.exerciseTitle!);
    public lineChartOptions: ChartOptions = {
        tooltips: {
            callbacks: {
                label(tooltipItem, data) {
                    let label = data.datasets![tooltipItem.datasetIndex!].label || '';

                    if (label) {
                        label += ': ';
                    }
                    label += Math.round((tooltipItem.yLabel as number) * 100) / 100;
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
    public lineChartColors: Color[] = [
        {
            borderColor: 'skyBlue',
            backgroundColor: 'skyBlue',
        },
        {
            borderColor: 'salmon',
            backgroundColor: 'salmon',
        },
        {
            borderColor: 'limeGreen',
            backgroundColor: 'limeGreen',
        },
    ];
    public lineChartLegend = true;
    public lineChartType: ChartType = 'line';
    public lineChartPlugins = [];

    constructor(
        private activatedRoute: ActivatedRoute,
        private alertService: JhiAlertService,
        private learningAnalyticsService: LearningAnalyticsService,
        private translateService: TranslateService,
    ) {}

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
        this.learningAnalyticsService
            .getCourseExerciseScores(this.courseId)
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
        const chartWidth = 150 * this.exerciseScores.length;
        this.chartDiv.nativeElement.setAttribute('style', `width: ${chartWidth}px;`);
        this.chartDirective.ngOnDestroy();
        this.chartDirective.chart = this.chartDirective.getChartBuilder(this.chartDirective.ctx);
        this.chartInstance = this.chartDirective.chart;
        const sortedExerciseScores = _.sortBy(this.exerciseScores, (exerciseScore) => exerciseScore.releaseDate);
        this.addData(this.chartInstance, sortedExerciseScores);
    }

    private addData(chart: Chart, exerciseScoresDTOs: ExerciseScoresDTO[]) {
        for (const exerciseScoreDTO of exerciseScoresDTOs) {
            chart.data.labels!.push(exerciseScoreDTO.exerciseTitle!);
            (chart.data.datasets![0].data as ChartPoint[])!.push({
                y: exerciseScoreDTO.scoreOfStudent,
                exerciseTitle: exerciseScoreDTO.exerciseTitle,
                exerciseType: exerciseScoreDTO.exerciseType,
            } as Chart.ChartPoint);
            (chart.data.datasets![1].data as ChartPoint[])!.push({
                y: exerciseScoreDTO.averageScoreAchieved,
                exerciseTitle: exerciseScoreDTO.exerciseTitle,
                exerciseType: exerciseScoreDTO.exerciseType,
            } as Chart.ChartPoint);
            (chart.data.datasets![2].data as ChartPoint[])!.push({
                y: exerciseScoreDTO.maxScoreAchieved,
                exerciseTitle: exerciseScoreDTO.exerciseTitle,
                exerciseType: exerciseScoreDTO.exerciseType,
            } as Chart.ChartPoint);
        }
        this.chartInstance.update();
    }
}
