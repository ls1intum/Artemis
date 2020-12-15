import { Component, Input, OnChanges, OnInit, SimpleChanges, ViewChild } from '@angular/core';
import { StatisticsService } from 'app/admin/statistics/statistics.service';
import { ChartDataSets, ChartOptions, ChartType } from 'chart.js';
import { BaseChartDirective, Label } from 'ng2-charts';
import { DataSet } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';
import { TranslateService } from '@ngx-translate/core';
import * as moment from 'moment';
import { Graphs, SpanType } from 'app/entities/statistics.model';

@Component({
    selector: 'jhi-statistics-graph',
    templateUrl: './statistics-graph.component.html',
})
export class StatisticsGraphComponent implements OnInit, OnChanges {
    // Inputs
    @Input()
    graphType: Graphs;
    @Input()
    currentSpan: SpanType;

    // html properties
    LEFT = false;
    RIGHT = true;
    SpanType = SpanType;
    Graphs = Graphs;

    // Histogram related properties
    public barChartOptions: ChartOptions = {};
    public barChartType: ChartType = 'bar';
    public lineChartType: ChartType = 'line';
    public amountOfStudents: string;
    public chartName: string;
    public barChartLegend = true;
    public chartTime: any;
    // Data
    public barChartLabels: Label[] = [];
    public chartData: ChartDataSets[] = [];
    public dataForSpanType: number[];

    // left arrow -> decrease, right arrow -> increase
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
        this.initializeChart();
    }

    ngOnInit() {
        this.amountOfStudents = this.translateService.instant('statistics.amountOfStudents');
        this.chartName = this.translateService.instant(`statistics.${this.graphType.toString().toLowerCase()}`);
        this.initializeChart();
    }

    private initializeChart(): void {
        this.createLabels();
        this.createCharts();
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
                this.chartTime = now.add(this.currentPeriod, 'months').format('MMMM YYYY');
                break;
            case SpanType.YEAR:
                this.barChartLabels = this.getMonths();
                this.chartTime = now.add(this.currentPeriod, 'years').format('YYYY');
                break;
        }
    }

    onTabChanged(span: SpanType): void {
        this.currentSpan = span;
        this.barChartLabels = [];
        this.currentPeriod = 0;
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
            legend: {
                display: true,
                align: 'start',
            },
        };
    }

    public switchTimeSpan(index: boolean): void {
        // eslint-disable-next-line chai-friendly/no-unused-expressions
        index ? (this.currentPeriod += 1) : (this.currentPeriod -= 1);
        this.initializeChart();
    }
}
