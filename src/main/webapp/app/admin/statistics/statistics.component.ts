import { Component, OnInit, ViewChild } from '@angular/core';
import { StatisticsService } from 'app/admin/statistics/statistics.service';
import { ChartDataSets, ChartOptions, ChartType } from 'chart.js';
import { BaseChartDirective, Label } from 'ng2-charts';
import { DataSet } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';
import { TranslateService } from '@ngx-translate/core';
import * as moment from 'moment';
import { Graphs, SpanType } from 'app/entities/statistics.model';

@Component({
    selector: 'jhi-statistics',
    templateUrl: './statistics.component.html',
})
export class StatisticsComponent implements OnInit {
    // html properties
    LEFT = false;
    RIGHT = true;
    SpanType = SpanType;
    Graphs = Graphs;
    currentSpan: SpanType = SpanType.WEEK;

    // Histogram related properties
    public barChartOptions: ChartOptions = {};
    public barChartType: ChartType = 'bar';
    public lineChartType: ChartType = 'line';
    public amountOfStudents: string;
    public barChartLegend = true;
    // submissions
    public submissionBarChartLabels: Label[] = [];
    public SubmissionsChartData: ChartDataSets[] = [];
    public submissionsForSpanType: number[];
    // active users
    public activeUsersBarChartLabels: Label[] = [];
    public activeUsersChartData: ChartDataSets[] = [];
    public activeUsersForSpanType: number[];
    // releasedExercises
    public releasedExercisesBarChartLabels: Label[] = [];
    public releasedExercisesChartData: ChartDataSets[] = [];
    public releasedExercisesForSpanType: number[];

    // left arrow -> decrease, right arrow -> increase
    private currentSubmissionPeriod = 0;
    private currentActiveUsersPeriod = 0;
    private currentReleasedExercisesPeriod = 0;

    @ViewChild(BaseChartDirective) chart: BaseChartDirective;

    constructor(private service: StatisticsService, private translateService: TranslateService) {}

    ngOnInit() {
        this.amountOfStudents = this.translateService.instant('statistics.amountOfStudents');
        this.initializeChart();
    }

    private initializeChart(): void {
        this.createSubmissionLabels();
        this.createActiveUsersLabels();
        this.createReleasedExercisesLabels();
        this.createCharts();
        this.service.getChartData(this.currentSpan, this.currentSubmissionPeriod, this.Graphs.SUBMISSIONS).subscribe((res: number[]) => {
            this.submissionsForSpanType = res;
            this.SubmissionsChartData = [
                {
                    label: this.amountOfStudents,
                    data: this.submissionsForSpanType,
                    backgroundColor: 'rgba(53,61,71,1)',
                    borderColor: 'rgba(53,61,71,1)',
                    hoverBackgroundColor: 'rgba(53,61,71,1)',
                },
            ];
        });
        this.service.getChartData(this.currentSpan, this.currentActiveUsersPeriod, this.Graphs.ACTIVE_USERS).subscribe((res: number[]) => {
            this.activeUsersForSpanType = res;
            this.activeUsersChartData = [
                {
                    label: this.amountOfStudents,
                    data: this.activeUsersForSpanType,
                    backgroundColor: 'rgba(53,61,71,1)',
                    borderColor: 'rgba(53,61,71,1)',
                    hoverBackgroundColor: 'rgba(53,61,71,1)',
                },
            ];
        });
        this.service.getChartData(this.currentSpan, this.currentActiveUsersPeriod, this.Graphs.RELEASED_EXERCISES).subscribe((res: number[]) => {
            this.releasedExercisesForSpanType = res;
            this.releasedExercisesChartData = [
                {
                    label: this.amountOfStudents,
                    data: this.releasedExercisesForSpanType,
                    backgroundColor: 'rgba(53,61,71,1)',
                    borderColor: 'rgba(53,61,71,1)',
                    hoverBackgroundColor: 'rgba(53,61,71,1)',
                },
            ];
        });
    }

    private createLabels(graph: Graphs): void {
        switch (this.currentSpan) {
            case SpanType.DAY:
                for (let i = 0; i < 24; i++) {
                    this.submissionBarChartLabels[i] = `${i}:00-${i + 1}:00`;
                }
                break;
            case SpanType.WEEK:
                this.submissionBarChartLabels = this.getWeekdays();
                break;
            case SpanType.MONTH:
                const startDate =
                    graph === Graphs.SUBMISSIONS ? moment().subtract(1 - this.currentSubmissionPeriod, 'months') : moment().subtract(1 - this.currentSubmissionPeriod, 'months');
                const endDate =
                    graph === Graphs.SUBMISSIONS ? moment().subtract(-this.currentSubmissionPeriod, 'months') : moment().subtract(-this.currentActiveUsersPeriod, 'months');
                const daysInMonth = endDate.diff(startDate, 'days');
                this.submissionBarChartLabels = this.getSubmissionLabelsForMonth(daysInMonth);
                break;
            case SpanType.YEAR:
                this.submissionBarChartLabels = this.getMonths();
                break;
        }
    }

    private createSubmissionLabels(): void {
        switch (this.currentSpan) {
            case SpanType.DAY:
                for (let i = 0; i < 24; i++) {
                    this.submissionBarChartLabels[i] = `${i}:00-${i + 1}:00`;
                }
                break;
            case SpanType.WEEK:
                this.submissionBarChartLabels = this.getWeekdays();
                break;
            case SpanType.MONTH:
                const startDate = moment().subtract(1 - this.currentSubmissionPeriod, 'months');
                const endDate = moment().subtract(-this.currentSubmissionPeriod, 'months');
                const daysInMonth = endDate.diff(startDate, 'days');
                this.submissionBarChartLabels = this.getSubmissionLabelsForMonth(daysInMonth);
                break;
            case SpanType.YEAR:
                this.submissionBarChartLabels = this.getMonths();
                break;
        }
    }
    private createActiveUsersLabels(): void {
        switch (this.currentSpan) {
            case SpanType.DAY:
                for (let i = 0; i < 24; i++) {
                    this.activeUsersBarChartLabels[i] = `${i}:00-${i + 1}:00`;
                }
                break;
            case SpanType.WEEK:
                this.activeUsersBarChartLabels = this.getWeekdays();
                break;
            case SpanType.MONTH:
                const startDate = moment().subtract(1 - this.currentActiveUsersPeriod, 'months');
                const endDate = moment().subtract(-this.currentActiveUsersPeriod, 'months');
                const daysInMonth = endDate.diff(startDate, 'days');
                this.activeUsersBarChartLabels = this.getActiveUsersLabelsForMonth(daysInMonth);
                break;
            case SpanType.YEAR:
                this.activeUsersBarChartLabels = this.getMonths();
                break;
        }
    }

    private createReleasedExercisesLabels(): void {
        switch (this.currentSpan) {
            case SpanType.DAY:
                for (let i = 0; i < 24; i++) {
                    this.releasedExercisesBarChartLabels[i] = `${i}:00-${i + 1}:00`;
                }
                break;
            case SpanType.WEEK:
                this.releasedExercisesBarChartLabels = this.getWeekdays();
                break;
            case SpanType.MONTH:
                const startDate = moment().subtract(1 - this.currentReleasedExercisesPeriod, 'months');
                const endDate = moment().subtract(-this.currentReleasedExercisesPeriod, 'months');
                const daysInMonth = endDate.diff(startDate, 'days');
                this.releasedExercisesBarChartLabels = this.getReleasedExercisesLabelsForMonth(daysInMonth);
                break;
            case SpanType.YEAR:
                this.releasedExercisesBarChartLabels = this.getMonths();
                break;
        }
    }

    onTabChanged(span: SpanType): void {
        this.currentSpan = span;
        this.submissionBarChartLabels = [];
        this.activeUsersBarChartLabels = [];
        this.releasedExercisesBarChartLabels = [];
        this.currentSubmissionPeriod = 0;
        this.currentActiveUsersPeriod = 0;
        this.currentReleasedExercisesPeriod = 0;
        this.initializeChart();
    }
    private getMonths(): string[] {
        const currentMonth = moment().month();
        const year = [
            this.translateService.instant('months.january'),
            this.translateService.instant('months.february'),
            this.translateService.instant('months.march'),
            this.translateService.instant('months.april'),
            this.translateService.instant('months.may'),
            this.translateService.instant('months.june'),
            this.translateService.instant('months.july'),
            this.translateService.instant('months.august'),
            this.translateService.instant('months.september'),
            this.translateService.instant('months.october'),
            this.translateService.instant('months.november'),
            this.translateService.instant('months.december'),
        ];
        const back = year.slice(currentMonth + 1, year.length);
        const front = year.slice(0, currentMonth + 1);
        return back.concat(front);
    }

    private getSubmissionLabelsForMonth(daysInMonth: number): string[] {
        const days: string[] = [];

        for (let i = 0; i < daysInMonth; i++) {
            days.push(
                moment()
                    .subtract(-this.currentSubmissionPeriod, 'months')
                    .subtract(daysInMonth - 1 - i, 'days')
                    .format('DD.MM'),
            );
        }
        return days;
    }

    private getActiveUsersLabelsForMonth(daysInMonth: number): string[] {
        const days: string[] = [];

        for (let i = 0; i < daysInMonth; i++) {
            days.push(
                moment()
                    .subtract(-this.currentActiveUsersPeriod, 'months')
                    .subtract(daysInMonth - 1 - i, 'days')
                    .format('DD.MM'),
            );
        }
        return days;
    }

    private getReleasedExercisesLabelsForMonth(daysInMonth: number): string[] {
        const days: string[] = [];

        for (let i = 0; i < daysInMonth; i++) {
            days.push(
                moment()
                    .subtract(-this.currentReleasedExercisesPeriod, 'months')
                    .subtract(daysInMonth - 1 - i, 'days')
                    .format('DD.MM'),
            );
        }
        return days;
    }

    private getWeekdays(): string[] {
        const currentDay = moment().day();
        const days = [
            this.translateService.instant('weekdays.monday'),
            this.translateService.instant('weekdays.tuesday'),
            this.translateService.instant('weekdays.wednesday'),
            this.translateService.instant('weekdays.thursday'),
            this.translateService.instant('weekdays.friday'),
            this.translateService.instant('weekdays.saturday'),
            this.translateService.instant('weekdays.sunday'),
        ];
        const back = days.slice(currentDay, days.length);
        const front = days.slice(0, currentDay);
        return back.concat(front);
    }

    private createCharts() {
        this.barChartOptions = {
            responsive: true,
            hover: {
                animationDuration: 0,
            },
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
                            ctx.fillText(String(data), bar._model.x, bar._model.y - 5);
                        });
                    });
                },
            },
            scales: {
                yAxes: [
                    {
                        ticks: {
                            beginAtZero: true,
                            min: 0,
                        },
                    },
                ],
            },
        };
    }

    public switchTimeSpan(graph: Graphs, index: boolean): void {
        switch (graph) {
            case Graphs.SUBMISSIONS:
                // eslint-disable-next-line chai-friendly/no-unused-expressions
                index ? (this.currentSubmissionPeriod += 1) : (this.currentSubmissionPeriod -= 1);
                break;
            case Graphs.ACTIVE_USERS:
                // eslint-disable-next-line chai-friendly/no-unused-expressions
                index ? (this.currentActiveUsersPeriod += 1) : (this.currentActiveUsersPeriod -= 1);
                break;
            case Graphs.RELEASED_EXERCISES:
                // eslint-disable-next-line chai-friendly/no-unused-expressions
                index ? (this.currentReleasedExercisesPeriod += 1) : (this.currentReleasedExercisesPeriod -= 1);
                break;
        }
        this.initializeChart();
    }
}
