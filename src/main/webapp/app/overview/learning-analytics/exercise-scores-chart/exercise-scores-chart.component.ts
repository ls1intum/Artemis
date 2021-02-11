import { AfterViewInit, Component, ElementRef, Input, ViewChild } from '@angular/core';
import * as Chart from 'chart.js';
import { ChartDataSets, ChartOptions, ChartType } from 'chart.js';
import { BaseChartDirective, Color, Label } from 'ng2-charts';
import { ExerciseScoresDTO, LearningAnalyticsService } from 'app/overview/learning-analytics/learning-analytics.service';
import { JhiAlertService } from 'ng-jhipster';
import { onError } from 'app/shared/util/global.utils';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import * as _ from 'lodash';

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
            label: 'Your Score',
            pointRadius: 8,
            pointStyle: 'circle',
            borderWidth: 5,
            lineTension: 0,
        },
        {
            fill: false,
            data: [],
            label: 'Average Score',
            pointRadius: 8,
            pointStyle: 'rect',
            borderWidth: 5,
            lineTension: 0,
            borderDash: [1, 1],
        },
        {
            fill: false,
            data: [],
            label: 'Maximum Score',
            pointRadius: 8,
            pointStyle: 'triangle',
            borderWidth: 5,
            lineTension: 0,
            borderDash: [15, 3, 3, 3],
        },
    ];
    public lineChartLabels: Label[] = this.exerciseScores.map((exerciseScoreDTO) => exerciseScoreDTO.exercise.title!);
    public lineChartOptions: ChartOptions = {
        responsive: true,
        maintainAspectRatio: false,
        title: {
            display: true,
            text: 'Scores',
            position: 'top',
            fontSize: 20,
        },
        legend: {
            position: 'bottom',
        },
        scales: {
            yAxes: [
                {
                    scaleLabel: {
                        display: true,
                        labelString: 'Score (%)',
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
                        labelString: 'Exercises',
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

    constructor(private activatedRoute: ActivatedRoute, private alertService: JhiAlertService, private learningAnalyticsService: LearningAnalyticsService) {}

    ngAfterViewInit() {
        this.chartInstance = this.chartDirective.chart;
        this.activatedRoute.parent!.params.subscribe((params) => {
            this.courseId = +params['courseId'];
            if (this.courseId) {
                this.loadData();
            }
        });
    }

    private loadData() {
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
        const sortedExerciseScores = _.sortBy(this.exerciseScores, (exerciseScore) => exerciseScore.exercise.releaseDate);
        this.addData(this.chartInstance, sortedExerciseScores);
    }

    private addData(chart: Chart, exerciseScoresDTOS: ExerciseScoresDTO[]) {
        for (const exerciseScoreDTO of exerciseScoresDTOS) {
            chart.data.labels!.push(exerciseScoreDTO.exercise.title!);
            chart.data.datasets![0].data!.push(exerciseScoreDTO.scoreOfStudent);
            chart.data.datasets![1].data!.push(exerciseScoreDTO.averageScoreAchieved);
            chart.data.datasets![2].data!.push(exerciseScoreDTO.maxScoreAchieved);
        }
        chart.update();
        const chartWidth = 150 * this.exerciseScores.length;
        this.chartDiv.nativeElement.setAttribute('style', `width: ${chartWidth}px`);
    }
}
