import { Component, OnInit, OnChanges, ViewChild } from '@angular/core';
import { StatisticsService } from 'app/admin/statistics/statistics.service';
import { SPAN_PATTERN } from 'app/app.constants';
import { ChartDataSets, ChartOptions, ChartType } from 'chart.js';
import { BaseChartDirective, Label } from 'ng2-charts';
import { DataSet } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';
import { TranslateService } from '@ngx-translate/core';
import * as moment from 'moment';
import { SpanType } from 'app/entities/statistics.model';

@Component({
    selector: 'jhi-statistics',
    templateUrl: './statistics.component.html',
})
export class StatisticsComponent implements OnInit, OnChanges {
    spanPattern = SPAN_PATTERN;
    span: SpanType = SpanType.WEEK;

    // Histogram related properties
    public histogramData: number[] = [];
    public barChartOptions: ChartOptions = {};
    public barChartLabels: Label[] = [];
    public barChartType: ChartType = 'bar';
    public barChartLegend = true;
    public UserLoginChartData: ChartDataSets[] = [];
    public SubmissionsChartData: ChartDataSets[] = [];

    @ViewChild(BaseChartDirective) chart: BaseChartDirective;

    constructor(private service: StatisticsService, private translateService: TranslateService) {}

    async ngOnInit() {
        await this.setBinWidth();
        await this.createChart();
    }

    async ngOnChanges() {}

    private async setBinWidth(): Promise<void> {
        switch (this.span) {
            case SpanType.DAY:
                this.histogramData = Array(24).fill(0);
                break;
            case SpanType.WEEK:
                this.histogramData = Array(7).fill(0);
                break;
            case SpanType.MONTH:
                this.histogramData = Array(moment().daysInMonth()).fill(0);
                break;
            case SpanType.YEAR:
                this.histogramData = Array(12).fill(0);
                break;
        }
    }

    async getSubmissions(): Promise<number[]> {
        return new Promise<number[]>((resolve, reject) => {
            this.service.getTotalSubmissions(this.span).subscribe((res: number[]) => {
                if (res !== null) {
                    resolve(res);
                } else {
                    reject('Submissions could not get fetched');
                }
            });
        });
    }

    onTabChanged(span: string): void {
        switch (span) {
            case 'Day':
                this.span = SpanType.DAY;
                break;
            case 'Week':
                this.span = SpanType.WEEK;
                break;
            case 'Month':
                this.span = SpanType.MONTH;
                break;
            case 'Year':
                this.span = SpanType.YEAR;
                break;
        }
    }

    private getWeekdays(): string[] {
        const days: string[] = [];

        for (let i = 0; i < 7; i++) {
            days.push(
                moment()
                    .subtract(6 - i, 'd')
                    .format('dddd'),
            );
        }
        return days;
    }

    private createLabels(): string[] {
        let labels: string[] = [];
        switch (this.span) {
            case SpanType.DAY:
                for (let i = 0; i < this.histogramData.length; i++) {
                    labels[i] = `${i}:00,${i + 1}:00`;
                }
                break;
            case SpanType.WEEK:
                labels = this.getWeekdays();
                break;
            case SpanType.MONTH:
                for (let i = 0; i < this.histogramData.length; i++) {
                    labels[i] = i + 1 + '';
                }
                break;
            case SpanType.YEAR:
                labels = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
                break;
        }
        return labels;
    }

    private async createChart() {
        this.barChartLabels = this.createLabels();
        this.UserLoginChartData = [
            {
                label: this.translateService.instant('statistics.amountOfStudents'),
                data: await this.getSubmissions(),
                backgroundColor: 'rgba(53,61,71,1)',
                borderColor: 'rgba(53,61,71,1)',
                hoverBackgroundColor: 'rgba(53,61,71,1)',
            },
        ];
        this.SubmissionsChartData = [
            {
                label: await this.translateService.instant('statistics.amountOfStudents'),
                data: await this.getSubmissions(),
                backgroundColor: 'rgba(53,61,71,1)',
                borderColor: 'rgba(53,61,71,1)',
                hoverBackgroundColor: 'rgba(53,61,71,1)',
            },
        ];
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
        };
    }
}
