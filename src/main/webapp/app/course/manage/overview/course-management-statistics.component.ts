import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Graphs, SpanType } from 'app/entities/statistics.model';
import { ChartDataSets, ChartType } from 'chart.js';
import { Label } from 'ng2-charts';
import { TranslateService } from '@ngx-translate/core';
import { DataSet } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';
import * as moment from 'moment';

@Component({
    selector: 'jhi-course-management-statistics',
    templateUrl: './course-management-statistics.component.html',
})
export class CourseManagementStatisticsComponent implements OnInit, OnChanges {
    @Input()
    courseId: number;

    @Input()
    amountOfStudentsInCourse: number;

    @Input()
    initialStats: number[];
    initialStatsReceived = false;

    currentSpan: SpanType;
    graphType: Graphs = Graphs.ACTIVE_STUDENTS;

    // Html properties
    LEFT = false;
    RIGHT = true;
    SpanType = SpanType;
    Graphs = Graphs;

    // Chart
    chartName: string;
    chartTime: any;

    // Histogram related properties
    barChartOptions: any = {};
    barChartType: ChartType = 'line';
    amountOfStudents: string;
    barChartLegend = false;

    // Data
    barChartLabels: Label[] = [];
    chartData: ChartDataSets[] = [];
    dataForSpanType: number[];

    // Left arrow -> decrease, right arrow -> increase
    private currentPeriod = 0;

    constructor(private service: CourseManagementService, private translateService: TranslateService) {}

    ngOnInit(): void {
        this.chartName = this.translateService.instant(`courseStatistics.${this.graphType.toString().toLowerCase()}`);
    }

    ngOnChanges() {
        this.amountOfStudents = this.translateService.instant('courseStatistics.amountOfStudents');

        // Only use the pre-loaded stats once
        if (this.initialStatsReceived || !this.initialStats) {
            return;
        }

        this.initialStatsReceived = true;
        this.createLabels();
        this.dataForSpanType = this.initialStats;
        this.chartData = [
            {
                label: this.amountOfStudents,
                data: this.dataForSpanType,
                backgroundColor: 'rgba(53,61,71,1)',
                borderColor: 'rgba(53,61,71,1)',
                fill: false,
                pointBackgroundColor: 'rgba(53,61,71,1)',
                pointHoverBorderColor: 'rgba(53,61,71,1)',
            },
        ];
        this.createChart();
    }

    private reloadChart() {
        this.createLabels();
        this.service.getStatisticsData(this.courseId, this.currentPeriod).subscribe((res: number[]) => {
            this.dataForSpanType = res;
            this.chartData = [
                {
                    label: this.amountOfStudents,
                    data: this.dataForSpanType,
                    backgroundColor: 'rgba(53,61,71,1)',
                    borderColor: 'rgba(53,61,71,1)',
                    fill: false,
                    pointBackgroundColor: 'rgba(53,61,71,1)',
                    pointHoverBorderColor: 'rgba(53,61,71,1)',
                },
            ];
        });
        this.createChart();
    }

    private createLabels() {
        const prefix = this.translateService.instant('calendar_week');
        const startDate = moment().subtract(3 + 4 * -this.currentPeriod, 'weeks');
        const endDate = this.currentPeriod !== 0 ? moment().subtract(4 * -this.currentPeriod, 'weeks') : moment();
        let currentWeek;
        for (let i = 0; i < 4; i++) {
            currentWeek = moment()
                .subtract(3 + 4 * -this.currentPeriod - i, 'weeks')
                .isoWeekday(1)
                .isoWeek();
            this.barChartLabels[i] = prefix + ' ' + currentWeek;
        }
        this.chartTime = startDate.isoWeekday(1).format('DD.MM.YYYY') + ' - ' + endDate.isoWeekday(7).format('DD.MM.YYYY');
    }

    switchTimeSpan(index: boolean): void {
        // eslint-disable-next-line chai-friendly/no-unused-expressions
        index ? (this.currentPeriod += 1) : (this.currentPeriod -= 1);
        this.reloadChart();
    }

    private createChart() {
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
                            max: this.amountOfStudentsInCourse ?? undefined,
                            precision: 0,
                        },
                    },
                ],
            },
        };
    }
}
