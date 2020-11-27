import { Component, OnInit, OnChanges, ViewChild } from '@angular/core';
import { StatisticsService } from 'app/admin/statistics/statistics.service';
import { SPAN_PATTERN } from 'app/app.constants';
import { ChartDataSets, ChartOptions, ChartType } from 'chart.js';
import { BaseChartDirective, Label } from 'ng2-charts';

export enum SpanType {
    DAY = 'DAY',
    WEEK = 'WEEK',
    MONTH = 'MONTH',
    YEAR = 'YEAR',
}

@Component({
    selector: 'jhi-statistics',
    templateUrl: './statistics.component.html',
})
export class JhiStatisticsComponent implements OnInit, OnChanges {
    spanPattern = SPAN_PATTERN;
    span: SpanType = SpanType.WEEK;
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
    public binWidth = 7;
    public histogramData: number[] = Array(this.binWidth).fill(0);
    public barChartOptions: ChartOptions = {};
    public barChartLabels: Label[] = [];
    public barChartType: ChartType = 'bar';
    public barChartLegend = true;
    public UserLoginChartData: ChartDataSets[] = [];
    public SubmissionsChartData: ChartDataSets[] = [];
    public testing: number[];

    @ViewChild(BaseChartDirective) chart: BaseChartDirective;

    constructor(private service: StatisticsService) {}

    async ngOnInit() {
        const value = await this.getSubmissions();

        this.createChart(value);
        this.onChangedUserSpan();
        this.onChangedActiveUserSpan();
        this.onChangedReleasedExerciseSpan();
        this.onChangedExerciseDeadlineSpan();
        this.onChangedConductedExamsSpan();
        this.onChangedActiveTutorsSpan();
        this.onChangedCreatedResultsSpan();
    }

    async ngOnChanges() {
        const value = await this.getSubmissions();
        this.createChart(value);
    }

    private setBinWidth(): void {
        switch (this.span) {
            case SpanType.DAY:
                this.histogramData = Array(24).fill(0);
                break;
            case SpanType.WEEK:
                this.histogramData = Array(7).fill(0);
                break;
            case SpanType.MONTH:
                const days = this.daysInMonth();
                this.histogramData = Array(days).fill(0);
                break;
            case SpanType.YEAR:
                this.histogramData = Array(12).fill(0);
                break;
        }
    }

    private daysInMonth(): number {
        return new Date(new Date().getFullYear(), new Date().getMonth(), 0).getDate();
    }

    async getSubmissions(): Promise<number[]> {
        return new Promise<number[]>((resolve, reject) => {
            this.service.getTotalSubmissions(this.span).subscribe((res: number[]) => {
                if (res !== null) {
                    resolve(res);
                } else {
                    reject('Submissions could not get fetched');
                }
            });
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
        this.service.getReleasedExercises(this.releasedExerciseSpan).subscribe((res: number) => {
            this.releasedExercises = res;
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

    onTabChanged(event: Event, span: String): void {
        switch (span) {
            case 'Day':
                this.span = SpanType.DAY;
                break;
            case 'Week':
                this.span = SpanType.WEEK;
                break;
            case 'Month':
                this.span = SpanType.MONTH;
                break;
            case 'Year':
                this.span = SpanType.YEAR;
                break;
        }
        console.log(this.span);
        // event.currentTarget!.className += " active";
    }

    private createChart(value: number[]) {
        this.setBinWidth();
        let labels: string[] = [];
        switch (this.span) {
            case SpanType.DAY:
                for (let i = 0; i < this.histogramData.length; i++) {
                    labels[i] = `${i}:00,${i + 1}:00`;
                }
                break;
            case SpanType.WEEK:
                labels = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
                break;
            case SpanType.MONTH:
                for (let i = 0; i < this.histogramData.length; i++) {
                    labels[i] = i + 1 + '';
                }
                break;
            case SpanType.YEAR:
                labels = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
                break;
        }
        this.barChartLabels = labels;
        // this.histogramData = [4002, 2020, 2088, 2050, 3660, 2110, 1202, 802, 2809, 2150, 2543, 909];
        this.UserLoginChartData = [
            {
                label: '# of students',
                data: value,
                backgroundColor: 'rgba(53,61,71,1)',
                borderColor: 'rgba(53,61,71,1)',
                hoverBackgroundColor: 'rgba(53,61,71,1)',
            },
        ];
        this.SubmissionsChartData = [
            {
                label: '# of students',
                data: value,
                backgroundColor: 'rgba(53,61,71,1)',
                borderColor: 'rgba(53,61,71,1)',
                hoverBackgroundColor: 'rgba(53,61,71,1)',
            },
        ];
        /*this.barChartOptions = {
            animation: {
                duration: 1,
                onComplete() {
                    const chartInstance = this.chart,
                        ctx = chartInstance.ctx;
                    ctx.textAlign = 'center';
                    ctx.textBaseline = 'bottom';

                    this.data.datasets.forEach(function (dataset: DataSet, j: number) {
                        const meta = chartInstance.controller.getDatasetMeta(j);
                        meta.data.forEach(function (bar: any, index: number) {
                            const data = dataset.data[index];
                            ctx.fillText(data, bar._model.x, bar._model.y - 5);
                        });
                    });
                },
            },
        };*/
    }
}
