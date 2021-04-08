import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { Graphs } from 'app/entities/statistics.model';
import { ChartDataSets, ChartType } from 'chart.js';
import { Label } from 'ng2-charts';
import { TranslateService } from '@ngx-translate/core';
import { ChangeDetectionStrategy } from '@angular/core';
import { DataSet } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';

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
                            const data = !!self.initialStats ? self.initialStats[index] : 0;
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
                            autoSkip: true,
                            precision: 0,
                            stepSize: 20,
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

                        return ' ' + self.initialStats[tooltipItem.index];
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
