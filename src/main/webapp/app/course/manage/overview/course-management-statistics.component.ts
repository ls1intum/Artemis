import { Component, Input, OnChanges } from '@angular/core';
import { Graphs } from 'app/entities/statistics.model';
import { ChartDataSets, ChartType } from 'chart.js';
import { Label } from 'ng2-charts';
import { TranslateService } from '@ngx-translate/core';

@Component({
    selector: 'jhi-course-management-statistics',
    templateUrl: './course-management-statistics.component.html',
})
export class CourseManagementStatisticsComponent implements OnChanges {
    @Input()
    courseId: number;

    @Input()
    amountOfStudentsInCourse: number;

    @Input()
    initialStats: number[];
    initialStatsReceived = false;

    graphType: Graphs = Graphs.ACTIVE_STUDENTS;

    // Chart
    chartName: string;

    // Histogram-related properties
    barChartOptions: any = {};
    barChartType: ChartType = 'line';
    amountOfStudents: string;
    barChartLegend = false;

    // Data
    barChartLabels: Label[] = [];
    chartData: ChartDataSets[] = [];
    dataForSpanType: number[] = [];

    constructor(private translateService: TranslateService) {}

    ngOnChanges() {
        this.amountOfStudents = this.translateService.instant('courseStatistics.amountOfStudents');

        // Only use the pre-loaded stats once
        if (this.initialStatsReceived || !this.initialStats || this.amountOfStudentsInCourse < 1) {
            return;
        }

        this.initialStatsReceived = true;

        for (let i = 0; i < 4; i++) {
            this.barChartLabels[i] = this.translateService.instant(`overview.${3 - i}_weeks_ago`);
        }

        for (const value of this.initialStats) {
            this.dataForSpanType.push((value * 100) / this.amountOfStudentsInCourse);
        }
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
                            autoSkip: true,
                            precision: 0,
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
