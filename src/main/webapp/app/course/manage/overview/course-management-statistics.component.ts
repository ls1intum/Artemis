import { Component, Input, OnInit } from '@angular/core';
import { Graphs } from 'app/entities/statistics.model';
import { ChartDataSets, ChartType } from 'chart.js';
import { Label } from 'ng2-charts';
import { TranslateService } from '@ngx-translate/core';
import { ChangeDetectionStrategy } from '@angular/core';

@Component({
    selector: 'jhi-course-management-statistics',
    templateUrl: './course-management-statistics.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseManagementStatisticsComponent implements OnInit {
    @Input()
    courseId: number;

    @Input()
    amountOfStudentsInCourse: number;

    @Input()
    initialStats: number[];

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

    ngOnInit() {
        this.amountOfStudents = this.translateService.instant('courseStatistics.amountOfStudents');

        for (let i = 0; i < 4; i++) {
            this.barChartLabels[i] = this.translateService.instant(`overview.${3 - i}_weeks_ago`);
        }

        if (this.amountOfStudentsInCourse > 0) {
            for (const value of this.initialStats) {
                this.dataForSpanType.push((value * 100) / this.amountOfStudentsInCourse);
            }
        } else {
            this.dataForSpanType = new Array(this.initialStats.length).fill(0);
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

        // Store a reference for the label function
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
