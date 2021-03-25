import { Component, Input, OnChanges, SimpleChanges, ViewChild } from '@angular/core';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import { ChartDataSets, ChartOptions, ChartType } from 'chart.js';
import { BaseChartDirective, Label } from 'ng2-charts';
import { DataSet } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';
import { TranslateService } from '@ngx-translate/core';
import * as moment from 'moment';
import { Graphs, SpanType, StatisticsView } from 'app/entities/statistics.model';

@Component({
    selector: 'jhi-statistics-graph',
    templateUrl: './statistics-graph.component.html',
})
export class StatisticsGraphComponent implements OnChanges {
    @Input()
    graphType: Graphs;
    @Input()
    currentSpan: SpanType;
    @Input()
    statisticsView: StatisticsView;
    @Input()
    courseId?: number;

    // Html properties
    LEFT = false;
    RIGHT = true;
    SpanType = SpanType;
    Graphs = Graphs;

    // Histogram related properties
    barChartOptions: ChartOptions = {};
    barChartType: ChartType = 'bar';
    lineChartType: ChartType = 'line';
    amountOfStudents: string;
    chartName: string;
    barChartLegend = false;
    chartTime: any;
    // Data
    barChartLabels: Label[] = [];
    chartData: ChartDataSets[] = [];
    dataForSpanType: number[];

    // Left arrow -> decrease, right arrow -> increase
    private currentPeriod = 0;

    @ViewChild(BaseChartDirective) chart: BaseChartDirective;

    constructor(private service: StatisticsService, private translateService: TranslateService) {}

    /**
     * Life cycle hook to indicate component changes
     * @param {SimpleChanges} changes - Changes being made to the component
     */
    ngOnChanges(changes: SimpleChanges): void {
        this.currentSpan = changes.currentSpan?.currentValue;
        this.barChartLabels = [];
        this.currentPeriod = 0;
        this.amountOfStudents = this.translateService.instant('statistics.amountOfStudents');
        this.chartName = this.translateService.instant(`statistics.${this.graphType.toString().toLowerCase()}`);
        this.initializeChart();
    }

    private initializeChart(): void {
        this.createLabels();
        this.chartData = [
            {
                label: this.amountOfStudents,
                data: new Array(this.barChartLabels.length).fill(0),
                backgroundColor: 'rgba(53,61,71,1)',
                borderColor: 'rgba(53,61,71,1)',
                hoverBackgroundColor: 'rgba(53,61,71,1)',
            },
        ];
        this.createCharts();
        if (this.statisticsView === StatisticsView.ARTEMIS) {
            this.service.getChartData(this.currentSpan, this.currentPeriod, this.graphType).subscribe((res: number[]) => {
                this.dataForSpanType = res;
                this.chartData = [
                    {
                        label: this.amountOfStudents,
                        data: this.dataForSpanType,
                        backgroundColor: 'rgba(53,61,71,1)',
                        borderColor: 'rgba(53,61,71,1)',
                        hoverBackgroundColor: 'rgba(53,61,71,1)',
                    },
                ];
            });
        } else {
            this.service.getChartDataForCourse(this.currentSpan, this.currentPeriod, this.graphType, this.courseId!).subscribe((res: number[]) => {
                this.dataForSpanType = res;
                this.chartData = [
                    {
                        label: this.amountOfStudents,
                        data: this.dataForSpanType,
                        backgroundColor: 'rgba(53,61,71,1)',
                        borderColor: 'rgba(53,61,71,1)',
                        hoverBackgroundColor: 'rgba(53,61,71,1)',
                    },
                ];
            });
        }
    }

    private createLabels(): void {
        const now = moment();
        let startDate;
        let endDate;
        switch (this.currentSpan) {
            case SpanType.DAY:
                for (let i = 0; i < 24; i++) {
                    this.barChartLabels[i] = `${i}:00-${i + 1}:00`;
                }
                this.chartTime = now.add(this.currentPeriod, 'days').format('DD.MM.YYYY');
                break;
            case SpanType.WEEK:
                this.barChartLabels = this.getWeekdays();
                startDate = moment().add(this.currentPeriod, 'weeks').subtract(6, 'days').format('DD.MM.YYYY');
                endDate = moment().add(this.currentPeriod, 'weeks').format('DD.MM.YYYY');
                this.chartTime = startDate + ' - ' + endDate;
                break;
            case SpanType.MONTH:
                startDate = moment().subtract(1 - this.currentPeriod, 'months');
                endDate = moment().subtract(-this.currentPeriod, 'months');
                const daysInMonth = endDate.diff(startDate, 'days');
                this.barChartLabels = this.getLabelsForMonth(daysInMonth);
                this.chartTime = now
                    .add(this.currentPeriod, 'months')
                    .subtract(Math.floor(this.barChartLabels.length / 2.0) - 1, 'days')
                    .format('MMMM YYYY');
                break;
            case SpanType.QUARTER:
                const prefix = this.translateService.instant('calendar_week');
                startDate = moment().subtract(11 + 12 * -this.currentPeriod, 'weeks');
                endDate = this.currentPeriod !== 0 ? moment().subtract(12 * -this.currentPeriod, 'weeks') : moment();
                let currentWeek;
                for (let i = 0; i < 12; i++) {
                    currentWeek = moment()
                        .subtract(11 + 12 * -this.currentPeriod - i, 'weeks')
                        .isoWeekday(1)
                        .isoWeek();
                    this.barChartLabels[i] = prefix + ' ' + currentWeek;
                }
                this.chartTime = startDate.isoWeekday(1).format('DD.MM.YYYY') + ' - ' + endDate.isoWeekday(7).format('DD.MM.YYYY');
                break;
            case SpanType.YEAR:
                this.barChartLabels = this.getMonths();
                this.chartTime = now.add(this.currentPeriod, 'years').subtract(5, 'months').format('YYYY');
                break;
        }
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
                    .subtract(-this.currentPeriod, 'months')
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
            layout: {
                padding: {
                    top: 20,
                },
            },
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
        index ? (this.currentPeriod += 1) : (this.currentPeriod -= 1);
        this.initializeChart();
    }
}
