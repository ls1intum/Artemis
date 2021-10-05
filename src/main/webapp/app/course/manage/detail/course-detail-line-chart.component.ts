import { Component, ContentChild, Input, OnChanges, TemplateRef } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs';
import { CourseManagementService } from '../course-management.service';
import { DataSet } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';
import { round } from 'app/shared/util/utils';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { Subject } from 'rxjs/internal/Subject';

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

    @ContentChild('tooltipTemplate') tooltipTemplate: TemplateRef<any>;
    @ContentChild('seriesTooltipTemplate') seriesTooltipTemplate: TemplateRef<any>;

    LEFT = false;
    RIGHT = true;
    displayedNumberOfWeeks = 16;

    // Chart
    chartTime: any;
    // Histogram related properties
    lineChartOptions: any = {};
    amountOfStudents: string;
    // Data
    data: number[] = [];
    absoluteData: number[] = [];

    // Left arrow -> decrease, right arrow -> increase
    private currentPeriod = 0;

    // Start new NGX variables
    view: [number, number] = [880, 450];
    chartColor: Color = {
        name: 'vivid',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: ['rgba(53,61,71,1)'],
    };
    legend = false;
    xAxis = true;
    yAxis = true;
    showYAxisLabel = false;
    showXAxisLabel = false;
    xAxisLabel = '';
    yAxisLabel = '';
    timeline = true;
    multi: any[];
    multiCopy = [
        {
            name: '',
            series: [{}],
        },
    ];

    onSelect(data: any): void {
        console.log('Item clicked', JSON.parse(JSON.stringify(data)));
    }

    onActivate(data: any): void {
        console.log('Activate', JSON.parse(JSON.stringify(data)));
    }

    onDeactivate(data: any): void {
        console.log('Deactivate', JSON.parse(JSON.stringify(data)));
    }

    // End new NGX variables

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
        this.multi = this.multiCopy;
    }

    /**
     * Reload the chart with the new data after an arrow is clicked
     */
    private reloadChart() {
        this.loading = true;
        this.createLabels();
        this.service.getStatisticsData(this.courseId, this.currentPeriod).subscribe((res: number[]) => {
            this.processDataAndCreateChart(res);
            this.multi = [...this.multiCopy];
        });
    }

    // Observable for update
    update$: Subject<any> = new Subject();

    // Update function
    updateChart() {
        this.update$.next(true);
    }

    /**
     * Takes the data, converts it into percentage and sets it accordingly
     */
    private processDataAndCreateChart(array: number[]) {
        if (this.numberOfStudentsInCourse > 0) {
            this.absoluteData = array;
            for (let i = 0; i < array.length; i++) {
                this.multiCopy[0].series[i]['value'] = round((array[i] / this.numberOfStudentsInCourse) * 100);
            }
        } else {
            this.absoluteData = array;
            for (let i = 0; i < this.displayedNumberOfWeeks; i++) {
                this.multiCopy[0].series[i]['value'] = 0;
            }
        }
        /*this.chartData = [
            {
                label: this.amountOfStudents,
                data: this.data,
                backgroundColor: 'rgba(53,61,71,1)',
                borderColor: 'rgba(53,61,71,1)',
                fill: false,
                pointBackgroundColor: 'rgba(53,61,71,1)',
                pointHoverBorderColor: 'rgba(53,61,71,1)',
            },
        ];*/
        this.loading = false;
        // this.multi = this.multiCopy;
    }

    private createLabels() {
        const prefix = this.translateService.instant('calendar_week');
        const startDate = dayjs().subtract(this.displayedNumberOfWeeks - 1 + this.displayedNumberOfWeeks * -this.currentPeriod, 'weeks');
        const endDate = this.currentPeriod !== 0 ? dayjs().subtract(this.displayedNumberOfWeeks * -this.currentPeriod, 'weeks') : dayjs();
        let currentWeek;
        for (let i = 0; i < this.displayedNumberOfWeeks; i++) {
            currentWeek = dayjs()
                .subtract(this.displayedNumberOfWeeks - 1 + this.displayedNumberOfWeeks * -this.currentPeriod - i, 'weeks')
                .isoWeekday(1)
                .isoWeek();
            this.multiCopy[0].series[i] = {};
            this.multiCopy[0].series[i]['name'] = prefix + ' ' + currentWeek;
        }
        this.chartTime = startDate.isoWeekday(1).format('DD.MM.YYYY') + ' - ' + endDate.isoWeekday(7).format('DD.MM.YYYY');
        this.multiCopy[0].name = this.amountOfStudents;
    }

    switchTimeSpan(index: boolean): void {
        // eslint-disable-next-line chai-friendly/no-unused-expressions
        index ? (this.currentPeriod += 1) : (this.currentPeriod -= 1);
        this.reloadChart();
    }

    formatYAxis(value: any) {
        return value.toLocaleString() + ' %';
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
