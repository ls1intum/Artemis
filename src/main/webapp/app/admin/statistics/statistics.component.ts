import { Component, OnInit, OnChanges } from '@angular/core';
import { StatisticsService } from 'app/admin/statistics/statistics.service';
import { SPAN_PATTERN } from 'app/app.constants';
import { ChartDataSets, ChartOptions, ChartType } from 'chart.js';
import { Label } from 'ng2-charts';
import { DataSet } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';

@Component({
    selector: 'jhi-statistics',
    templateUrl: './statistics.component.html',
})
export class JhiStatisticsComponent implements OnInit, OnChanges {
    spanPattern = SPAN_PATTERN;
    userSpan = 7;
    activeUserSpan = 7;
    submissionSpan = 7;
    releasedExerciseSpan = 7;
    exerciseDeadlineSpan = 7;
    conductedExamsSpan = 7;
    activeTutorsSpan = 7;
    createdResultsSpan = 7;
    loggedInUsers = 0;
    activeUsers = 0;
    totalSubmissions = 0;
    releasedExercises = 0;
    exerciseDeadlines = 0;
    conductedExams = 0;
    activeTutors = 0;
    createdResults = 0;
    examParticipations = 0;
    examRegistrations = 0;
    resultFeedbacks = 0;

    // Histogram related properties
    public binWidth = 4;
    public histogramData: number[] = Array(this.binWidth).fill(0);
    public barChartOptions: ChartOptions = {};
    public barChartLabels: Label[] = [];
    public barChartType: ChartType = 'bar';
    public barChartLegend = true;
    public barChartData: ChartDataSets[] = [];

    constructor(private service: StatisticsService) {}

    ngOnInit() {
        this.onChangedUserSpan();
        this.onChangedActiveUserSpan();
        this.onChangedSubmissionSpan();
        this.onChangedReleasedExerciseSpan();
        this.onChangedExerciseDeadlineSpan();
        this.onChangedConductedExamsSpan();
        this.onChangedActiveTutorsSpan();
        this.onChangedCreatedResultsSpan();
    }

    ngOnChanges(): void {
        this.service.getloggedUsers(this.userSpan).subscribe((res: number) => {
            this.loggedInUsers = res;
        });
    }

    onChangedUserSpan(): void {
        this.service.getloggedUsers(this.userSpan).subscribe((res: number) => {
            this.loggedInUsers = res;
        });
    }
    onChangedActiveUserSpan(): void {
        this.service.getActiveUsers(this.activeUserSpan).subscribe((res: number) => {
            this.activeUsers = res;
        });
    }
    onChangedSubmissionSpan(): void {
        this.service.getTotalSubmissions(this.submissionSpan).subscribe((res: number) => {
            this.totalSubmissions = res;
        });
    }

    onChangedReleasedExerciseSpan(): void {
        this.service.getReleasedExercises(this.releasedExerciseSpan).subscribe((res: number) => {
            this.releasedExercises = res;
        });
    }

    onChangedExerciseDeadlineSpan(): void {
        this.service.getExerciseDeadlines(this.exerciseDeadlineSpan).subscribe((res: number) => {
            this.exerciseDeadlines = res;
        });
    }

    onChangedConductedExamsSpan(): void {
        this.service.getConductedExams(this.conductedExamsSpan).subscribe((res: number) => {
            this.conductedExams = res;
        });

        this.service.getExamParticipations(this.conductedExamsSpan).subscribe((res: number) => {
            this.examParticipations = res;
        });

        this.service.getExamRegistrations(this.conductedExamsSpan).subscribe((res: number) => {
            this.examRegistrations = res;
        });
    }

    onChangedActiveTutorsSpan(): void {
        this.service.getActiveTutors(this.activeTutorsSpan).subscribe((res: number) => {
            this.activeTutors = res;
        });
    }

    onChangedCreatedResultsSpan(): void {
        this.service.getCreatedResults(this.createdResultsSpan).subscribe((res: number) => {
            this.createdResults = res;
        });

        this.service.getResultFeedbacks(this.createdResultsSpan).subscribe((res: number) => {
            this.resultFeedbacks = res;
        });
    }

    /*private createChart() {
        const labels: string[] = [];
        let i;
        for (i = 0; i < this.histogramData.length; i++) {
            labels[i] = `[${i},${(i + 1)}`;
            labels[i] += i === this.histogramData.length - 1 ? ']' : ')';
        }
        this.barChartLabels = labels;

        const component = this;

        this.barChartOptions = {
            responsive: true,
            maintainAspectRatio: false,
            legend: {
                align: 'start',
                position: 'bottom',
            },
            scales: {
                yAxes: [
                    {
                        scaleLabel: {
                            display: true,
                            labelString: this.translateService.instant('artemisApp.examScores.yAxes'),
                        },
                        ticks: {
                            maxTicksLimit: 11,
                            beginAtZero: true,
                            precision: 0,
                            min: 0,
                            max: this.calculateTickMax(),
                        } as LinearTickOptions,
                    },
                ],
                xAxes: [
                    {
                        scaleLabel: {
                            display: true,
                            labelString: this.translateService.instant('artemisApp.examScores.xAxes'),
                        },
                    },
                ],
            },
            hover: {
                animationDuration: 0,
            },
            animation: {
                duration: 1,
                onComplete() {
                    const chartInstance = this.chart,
                        ctx = chartInstance.ctx;

                    ctx.font = Chart.helpers.fontString(Chart.defaults.global.defaultFontSize, Chart.defaults.global.defaultFontStyle, Chart.defaults.global.defaultFontFamily);
                    ctx.textAlign = 'center';
                    ctx.textBaseline = 'bottom';

                    this.data.datasets.forEach(function (dataset: DataSet, j: number) {
                        const meta = chartInstance.controller.getDatasetMeta(j);
                        meta.data.forEach(function (bar: any, index: number) {
                            const data = dataset.data[index];
                            ctx.fillText(data, bar._model.x, bar._model.y - 20);
                            ctx.fillText(`(${component.roundAndPerformLocalConversion((data * 100) / component.noOfExamsFiltered, 2, 2)}%)`, bar._model.x, bar._model.y - 5);
                        });
                    });
                },
            },
        };

        this.barChartData = [
            {
                label: '# of students',
                data: this.histogramData,
                backgroundColor: 'rgba(0,0,0,0.5)',
            },
        ];
    }*/
}
