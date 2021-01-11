import { Component, Input, OnInit } from '@angular/core';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { Graphs, SpanType } from 'app/entities/statistics.model';
import { ChartDataSets, ChartOptions, ChartType } from 'chart.js';
import { Label } from 'ng2-charts';
import { TranslateService } from '@ngx-translate/core';
import { DataSet } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';
import * as moment from 'moment';

@Component({
    selector: 'jhi-course-management-statistics',
    templateUrl: './course-management-statistics.component.html',
})
export class CourseManagementStatisticsComponent implements OnInit {
    @Input()
    courseId: number;

    currentSpan: SpanType;
    graphType: Graphs = Graphs.ACTIVE_STUDENTS;

    // Html properties
    LEFT = false;
    RIGHT = true;
    SpanType = SpanType;
    Graphs = Graphs;

    // Chart
    // chartName: string;
    chartName = 'ACTIVE STUDENTS';
    chartTime: any;

    // Histogram related properties
    barChartOptions: ChartOptions = {};
    barChartType: ChartType = 'bar';
    amountOfStudents: string;
    barChartLegend = false;
    // Data
    barChartLabels: Label[] = [];
    chartData: ChartDataSets[] = [];
    dataForSpanType: number[];

    // Left arrow -> decrease, right arrow -> increase
    private currentPeriod = 0;

    // mock
    courseScore = 100;

    constructor(private service: CourseManagementService, private translateService: TranslateService) {}

    ngOnInit(): void {
        this.amountOfStudents = this.translateService.instant('courseStatistics.amountOfStudents');
        this.chartName = this.translateService.instant(`courseStatistics.${this.graphType.toString().toLowerCase()}`);
        this.initializeChart();
    }

    private initializeChart() {
        this.createLabels();
        this.service.getStatisticsData(this.courseId, this.currentPeriod).subscribe((res: number[]) => {
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
        this.createChart();
    }

    private createLabels() {
        const prefix = this.translateService.instant('calendar_week');
        const startDate = moment().subtract(11 + 12 * -this.currentPeriod, 'weeks');
        const endDate = this.currentPeriod !== 0 ? moment().subtract(12 * -this.currentPeriod, 'weeks') : moment();
        let currentWeek;
        for (let i = 0; i < 12; i++) {
            currentWeek = moment()
                .subtract(11 + 12 * -this.currentPeriod - i, 'weeks')
                .isoWeekday(1)
                .isoWeek();
            this.barChartLabels[i] = prefix + ' ' + currentWeek;
        }
        this.chartTime = startDate.isoWeekday(1).format('DD.MM.YYYY') + ' - ' + endDate.isoWeekday(7).format('DD.MM.YYYY');
    }

    switchTimeSpan(index: boolean): void {
        // eslint-disable-next-line chai-friendly/no-unused-expressions
        index ? (this.currentPeriod += 1) : (this.currentPeriod -= 1);
        this.initializeChart();
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
                        },
                    },
                ],
            },
        };
    }
}
