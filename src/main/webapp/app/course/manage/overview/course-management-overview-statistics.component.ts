import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { Graphs } from 'app/entities/statistics.model';
import { ChartDataset, ChartOptions, ChartType } from 'chart.js';
import { Label } from 'ng2-charts';
import { TranslateService } from '@ngx-translate/core';
import { ChangeDetectionStrategy } from '@angular/core';
import Chart from 'chart.js/auto';
import ChartDataLabels from 'chartjs-plugin-datalabels';

Chart.register(ChartDataLabels);
Chart.defaults.plugins.datalabels!.display = false;

@Component({
    selector: 'jhi-course-management-overview-statistics',
    templateUrl: './course-management-overview-statistics.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CourseManagementOverviewStatisticsComponent implements OnInit, OnChanges {
    @Input()
    amountOfStudentsInCourse: number;

    @Input()
    initialStats: number[] | undefined;

    loading = true;
    graphType: Graphs = Graphs.ACTIVE_STUDENTS;

    // Chart
    chartName: string;

    // Histogram-related properties
    barChartOptions: ChartOptions = {};
    barChartType: ChartType = 'line';
    amountOfStudents: string;
    barChartLegend = false;

    // Data
    barChartLabels: Label[] = [];
    chartData: ChartDataset[] = [];
    dataForSpanType: number[] = [];

    constructor(private translateService: TranslateService) {}

    ngOnInit() {
        this.amountOfStudents = this.translateService.instant('artemisApp.courseStatistics.amountOfStudents');

        for (let i = 0; i < 4; i++) {
            this.barChartLabels[i] = this.translateService.instant(`overview.${3 - i}_weeks_ago`);
        }

        this.createChartData();

        // Store a reference for the label function
        const self = this;
        this.barChartOptions = {
            layout: {
                padding: {
                    top: 20,
                },
            },
            responsive: true,
            animation: {
                duration: 1,
            },
            scales: {
                y: {
                    beginAtZero: true,
                    min: 0,
                    max: 100,
                    ticks: {
                        autoSkip: true,
                        precision: 0,
                        stepSize: 20,
                        callback(value: number) {
                            return value + '%';
                        },
                    },
                },
            },
            plugins: {
                datalabels: {
                    display: true,
                    anchor: 'end',
                    align: 'end',
                    offset: 0,
                },
                legend: {
                    display: false,
                },
                tooltip: {
                    enabled: true,
                    callbacks: {
                        label(tooltipItem: any) {
                            if (!self.initialStats) {
                                return ' 0';
                            }

                            return ' ' + self.initialStats[tooltipItem.dataIndex];
                        },
                    },
                },
            },
        };
    }

    ngOnChanges() {
        if (!!this.initialStats) {
            this.loading = false;
            this.createChartData();
        }
    }

    private createChartData() {
        if (this.amountOfStudentsInCourse > 0 && !!this.initialStats) {
            this.dataForSpanType = [];
            for (const value of this.initialStats) {
                this.dataForSpanType.push((value * 100) / this.amountOfStudentsInCourse);
            }
        } else {
            this.dataForSpanType = new Array(4).fill(0);
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
    }
}
