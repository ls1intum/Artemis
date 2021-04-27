import { Component, Input, OnChanges } from '@angular/core';
import { ChartDataSets, ChartType } from 'chart.js';
import { Label } from 'ng2-charts';
import { TranslateService } from '@ngx-translate/core';
import * as moment from 'moment';
import { Graphs, SpanType } from 'app/entities/statistics.model';
import { CourseManagementService } from '../course-management.service';

@Component({
    selector: 'jhi-course-detail-bar-chart',
    templateUrl: './course-detail-bar-chart.component.html',
})
export class CourseDetailBarChartComponent implements OnChanges {
    @Input()
    courseId: number;
    @Input()
    numberOfStudentsInCourse: number;
    @Input()
    initialStats: number[];
    initialStatsReceived = false;

    graphType: Graphs = Graphs.ACTIVE_STUDENTS;

    LEFT = false;
    RIGHT = true;
    Graphs = Graphs;

    // Chart
    chartTime: any;
    // Histogram related properties
    barChartOptions: any = {};
    barChartType: ChartType = 'line';
    amountOfStudents: string;
    barChartLegend = false;
    // Data
    barChartLabels: Label[] = [];
    chartData: ChartDataSets[] = [];
    data: number[] = [];

    // Left arrow -> decrease, right arrow -> increase
    private currentPeriod = 0;

    constructor(private service: CourseManagementService, private translateService: TranslateService) {}

    ngOnChanges() {
        this.amountOfStudents = this.translateService.instant('artemisApp.courseStatistics.amountOfStudents');
        // Only use the pre-loaded stats once
        if (this.initialStatsReceived || !this.initialStats) {
            return;
        }
        this.initialStatsReceived = true;
        this.createLabels();
        if (this.numberOfStudentsInCourse > 0) {
            for (const value of this.initialStats) {
                this.data.push(Math.round((value / this.numberOfStudentsInCourse) * 100));
            }
        } else {
            this.data = new Array(this.initialStats.length).fill(0);
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
        this.createChart();
    }

    private reloadChart() {
        this.createLabels();
        this.service.getStatisticsData(this.courseId, this.currentPeriod).subscribe((res: number[]) => {
            if (this.numberOfStudentsInCourse > 0) {
                this.data = [];
                for (const value of res) {
                    this.data.push(Math.round((value / this.numberOfStudentsInCourse) * 100));
                }
            } else {
                this.data = new Array(res.length).fill(0);
            }
            // this.data = res;
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
        const self = this;
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
                        return ' ' + self.initialStats[tooltipItem.index];
                    },
                },
            },
        };
    }
}
