import { Component, OnInit, ViewChild } from '@angular/core';
import { StatisticsService } from 'app/admin/statistics/statistics.service';
import { ChartDataSets, ChartOptions, ChartType } from 'chart.js';
import { BaseChartDirective, Label } from 'ng2-charts';
import { DataSet } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';
import { TranslateService } from '@ngx-translate/core';
import * as moment from 'moment';
import { SpanType } from 'app/entities/statistics.model';

@Component({
    selector: 'jhi-statistics',
    templateUrl: './statistics.component.html',
})
export class StatisticsComponent implements OnInit {
    // html properties
    LEFT = false;
    RIGHT = true;
    SpanType = SpanType;
    currentSpan: SpanType = SpanType.WEEK;

    // Histogram related properties
    public barChartOptions: ChartOptions = {};
    public barChartLabels: Label[] = [];
    public barChartType: ChartType = 'bar';
    public barChartLegend = true;
    public SubmissionsChartData: ChartDataSets[] = [];
    public amountOfStudents: string;
    public submissionsForSpanType: number[];
    private currentSubmissionPeriod = 0; // left arrow -> decrease, right arrow -> increase

    @ViewChild(BaseChartDirective) chart: BaseChartDirective;

    constructor(private service: StatisticsService, private translateService: TranslateService) {}

    ngOnInit() {
        this.amountOfStudents = this.translateService.instant('statistics.amountOfStudents');
        this.initializeChart();
    }
    private initializeChart(): void {
        this.createLabels();
        this.service.getTotalSubmissions(this.span, this.currentSubmissionPeriod).subscribe((res: number[]) => {
            this.submissionsForSpanType = res;
            this.createChart();
        });
    }

    private createLabels(): void {
        switch (this.currentSpan) {
            case SpanType.DAY:
                for (let i = 0; i < 24; i++) {
                    this.barChartLabels[i] = `${i}:00-${i + 1}:00`;
                }
                break;
            case SpanType.WEEK:
                this.barChartLabels = this.getWeekdays();
                break;
            case SpanType.MONTH:
                const startDate = moment().subtract(1 - this.currentSubmissionPeriod, 'months');
                const endDate = moment().subtract(-this.currentSubmissionPeriod, 'months');
                const daysInMonth = endDate.diff(startDate, 'days');
                this.barChartLabels = this.getLabelsForMonth(daysInMonth);
                break;
            case SpanType.YEAR:
                this.barChartLabels = this.getMonths();
                break;
        }
    }

    onTabChanged(span: SpanType): void {
        this.currentSpan = span;
        this.barChartLabels = [];
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

    private getLabelsForMonth(daysInMonth: number): string[] {
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

    private createChart() {
        this.SubmissionsChartData = [
            {
                label: this.amountOfStudents,
                data: this.submissionsForSpanType,
                backgroundColor: 'rgba(53,61,71,1)',
                borderColor: 'rgba(53,61,71,1)',
                hoverBackgroundColor: 'rgba(53,61,71,1)',
            },
        ];
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

    public switchTimeSpan(index: boolean): void {
        // eslint-disable-next-line chai-friendly/no-unused-expressions
        index ? (this.currentSubmissionPeriod += 1) : (this.currentSubmissionPeriod -= 1);
        this.initializeChart();
    }
}
