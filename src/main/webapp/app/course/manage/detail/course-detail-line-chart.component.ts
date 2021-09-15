import { Component, Input, OnChanges } from '@angular/core';
import { ChartDataSets, ChartType } from 'chart.js';
import { Label } from 'ng2-charts';
import { TranslateService } from '@ngx-translate/core';
import * as moment from 'moment';
import { CourseManagementService } from '../course-management.service';
import { DataSet } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';
import { round } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-course-detail-line-chart',
    templateUrl: './course-detail-line-chart.component.html',
    styleUrls: ['./course-detail-line-chart.component.scss'],
})
export class CourseDetailLineChartComponent implements OnChanges {
    @Input()
    courseId: number;
    @Input()
    numberOfStudentsInCourse: number;
    @Input()
    initialStats: number[] | undefined;
    initialStatsReceived = false;
    loading = true;

    LEFT = false;
    RIGHT = true;
    displayedNumberOfWeeks = 16;

    // Chart
    chartTime: any;
    // Histogram related properties
    lineChartOptions: any = {};
    lineChartType: ChartType = 'line';
    amountOfStudents: string;
    lineChartLegend = false;
    // Data
    lineChartLabels: Label[] = [];
    chartData: ChartDataSets[] = [{ data: [] }];
    data: number[] = [];
    absoluteData: number[] = [];

    // Left arrow -> decrease, right arrow -> increase
    private currentPeriod = 0;

    constructor(private service: CourseManagementService, private translateService: TranslateService) {}

    ngOnChanges() {
        this.loading = true;
        this.amountOfStudents = this.translateService.instant('artemisApp.courseStatistics.amountOfStudents');
        // Only use the pre-loaded stats once
        if (this.initialStatsReceived || !this.initialStats) {
            return;
        }
        this.initialStatsReceived = true;
        this.createLabels();
        this.processDataAndCreateChart(this.initialStats);
    }

    /**
     * Reload the chart with the new data after an arrow is clicked
     */
    private reloadChart() {
        this.loading = true;
        this.createLabels();
        this.service.getStatisticsData(this.courseId, this.currentPeriod).subscribe((res: number[]) => {
            this.processDataAndCreateChart(res);
        });
    }

    /**
     * Takes the data, converts it into percentage and sets it accordingly
     */
    private processDataAndCreateChart(array: number[]) {
        if (this.numberOfStudentsInCourse > 0) {
            this.absoluteData = array;
            this.data = [];
            for (const value of array) {
                this.data.push(round((value / this.numberOfStudentsInCourse) * 100));
            }
        } else {
            this.absoluteData = array;
            this.data = new Array(array.length).fill(0);
        }
        this.chartData = [
            {
                label: this.amountOfStudents,
                data: this.data,
                backgroundColor: 'rgba(53,61,71,1)',
                borderColor: 'rgba(53,61,71,1)',
                fill: false,
                pointBackgroundColor: 'rgba(53,61,71,1)',
                pointHoverBorderColor: 'rgba(53,61,71,1)',
            },
        ];
        this.defineChartOptions();
        this.loading = false;
    }

    private createLabels() {
        const prefix = this.translateService.instant('calendar_week');
        const startDate = moment().subtract(this.displayedNumberOfWeeks - 1 + this.displayedNumberOfWeeks * -this.currentPeriod, 'weeks');
        const endDate = this.currentPeriod !== 0 ? moment().subtract(this.displayedNumberOfWeeks * -this.currentPeriod, 'weeks') : moment();
        let currentWeek;
        for (let i = 0; i < this.displayedNumberOfWeeks; i++) {
            currentWeek = moment()
                .subtract(this.displayedNumberOfWeeks - 1 + this.displayedNumberOfWeeks * -this.currentPeriod - i, 'weeks')
                .isoWeekday(1)
                .isoWeek();
            this.lineChartLabels[i] = prefix + ' ' + currentWeek;
        }
        this.chartTime = startDate.isoWeekday(1).format('DD.MM.YYYY') + ' - ' + endDate.isoWeekday(7).format('DD.MM.YYYY');
    }

    switchTimeSpan(index: boolean): void {
        // eslint-disable-next-line chai-friendly/no-unused-expressions
        index ? (this.currentPeriod += 1) : (this.currentPeriod -= 1);
        this.reloadChart();
    }

    private defineChartOptions() {
        const self = this;
        this.lineChartOptions = {
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
                            const data = !!self.absoluteData ? self.absoluteData[index] : 0;
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
                            max: 100,
                            precision: 0,
                            autoSkip: true,
                            callback(value: number) {
                                return value + '%';
                            },
                        },
                    },
                ],
            },
            tooltips: {
                enabled: true,
                callbacks: {
                    label(tooltipItem: any) {
                        if (!self.initialStats) {
                            return ' 0';
                        }
                        return ' ' + self.absoluteData[tooltipItem.index];
                    },
                },
            },
        };
    }
}
