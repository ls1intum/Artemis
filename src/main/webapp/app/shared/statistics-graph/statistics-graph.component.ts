import { Component, Input, OnChanges, SimpleChanges } from '@angular/core';
import { StatisticsService } from 'app/shared/statistics-graph/statistics.service';
import dayjs from 'dayjs/esm';
import { GraphColors, Graphs, SpanType, StatisticsView } from 'app/entities/statistics.model';
import { faArrowLeft, faArrowRight } from '@fortawesome/free-solid-svg-icons';
import { yAxisTickFormatting } from 'app/shared/statistics-graph/statistics-graph.utils';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { TranslateService } from '@ngx-translate/core';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';

@Component({
    selector: 'jhi-statistics-graph',
    templateUrl: './statistics-graph.component.html',
    styleUrls: ['../chart/vertical-bar-chart.scss'],
})
export class StatisticsGraphComponent implements OnChanges {
    @Input()
    graphType: Graphs;
    @Input()
    currentSpan: SpanType;
    @Input()
    statisticsView: StatisticsView;
    @Input()
    entityId?: number;

    // Html properties
    LEFT = false;
    RIGHT = true;
    SpanType = SpanType;
    Graphs = Graphs;

    // Histogram related properties
    chartName: string;
    barChartLegend = false;
    chartTime: any;
    // Data
    barChartLabels: string[] = [];
    dataForSpanType: number[];

    // ngx
    ngxData: NgxChartsSingleSeriesDataEntry[] = [];
    ngxColor: Color = {
        name: 'Statistics',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [GraphColors.DARK_BLUE],
    };
    tooltipTranslation: string;
    yScaleMax: number;
    yAxisTickFormatting = yAxisTickFormatting;

    // Left arrow -> decrease, right arrow -> increase
    private currentPeriod = 0;

    // Icons
    faArrowLeft = faArrowLeft;
    faArrowRight = faArrowRight;

    constructor(private service: StatisticsService, private translateService: TranslateService) {
        this.translateService.onLangChange.subscribe(() => {
            this.onSystemLanguageChange();
        });
    }

    /**
     * Life cycle hook to indicate component changes
     * @param {SimpleChanges} changes - Changes being made to the component
     */
    ngOnChanges(changes: SimpleChanges): void {
        this.currentSpan = changes.currentSpan?.currentValue;
        this.barChartLabels = [];
        this.currentPeriod = 0;
        this.chartName = `statistics.${this.graphType.toString().toLowerCase()}`;
        this.tooltipTranslation = `statistics.${this.graphType.toString().toLowerCase()}Title`;
        this.initializeChart();
    }

    private initializeChart(): void {
        this.createLabels();
        if (this.statisticsView === StatisticsView.ARTEMIS) {
            this.service.getChartData(this.currentSpan, this.currentPeriod, this.graphType).subscribe((res: number[]) => {
                this.dataForSpanType = res;
                this.pushToData();
            });
        } else {
            this.service.getChartDataForContent(this.currentSpan, this.currentPeriod, this.graphType, this.statisticsView, this.entityId!).subscribe((res: number[]) => {
                this.dataForSpanType = res;
                this.pushToData();
            });
        }
    }

    private createLabels(): void {
        const now = dayjs();
        let startDate;
        let endDate;
        switch (this.currentSpan) {
            case SpanType.DAY:
                for (let i = 0; i < 24; i++) {
                    this.barChartLabels[i] = `${i}:00-${i + 1}:00`;
                }
                this.chartTime = now.add(this.currentPeriod, 'days').format('DD.MM.YYYY');
                break;
            case SpanType.WEEK:
                this.barChartLabels = this.getWeekdays();
                startDate = dayjs().add(this.currentPeriod, 'weeks').subtract(6, 'days').format('DD.MM.YYYY');
                endDate = dayjs().add(this.currentPeriod, 'weeks').format('DD.MM.YYYY');
                this.chartTime = startDate + ' - ' + endDate;
                break;
            case SpanType.MONTH:
                startDate = dayjs().subtract(1 - this.currentPeriod, 'months');
                endDate = dayjs().subtract(-this.currentPeriod, 'months');
                const daysInMonth = endDate.diff(startDate, 'days');
                this.barChartLabels = this.getLabelsForMonth(daysInMonth);
                this.chartTime = now
                    .add(this.currentPeriod, 'months')
                    .subtract(Math.floor(this.barChartLabels.length / 2.0) - 1, 'days')
                    .format('MMMM YYYY');
                break;
            case SpanType.QUARTER:
                const prefix = this.translateService.instant('calendar_week');
                startDate = dayjs().subtract(11 + 12 * -this.currentPeriod, 'weeks');
                endDate = this.currentPeriod !== 0 ? dayjs().subtract(12 * -this.currentPeriod, 'weeks') : dayjs();
                let currentWeek;
                for (let i = 0; i < 12; i++) {
                    currentWeek = dayjs()
                        .subtract(11 + 12 * -this.currentPeriod - i, 'weeks')
                        .isoWeekday(1)
                        .isoWeek();
                    this.barChartLabels[i] = prefix + ' ' + currentWeek;
                }
                this.chartTime = startDate.isoWeekday(1).format('DD.MM.YYYY') + ' - ' + endDate.isoWeekday(7).format('DD.MM.YYYY');
                break;
            case SpanType.YEAR:
                this.barChartLabels = this.getMonths();
                this.chartTime = now.add(this.currentPeriod, 'years').subtract(5, 'months').format('YYYY');
                break;
        }
    }

    private getMonths(): string[] {
        const currentMonth = dayjs().month();
        const year = [
            this.translateService.instant('months.january'),
            this.translateService.instant('months.february'),
            this.translateService.instant('months.march'),
            this.translateService.instant('months.april'),
            this.translateService.instant('months.may'),
            this.translateService.instant('months.june'),
            this.translateService.instant('months.july'),
            this.translateService.instant('months.august'),
            this.translateService.instant('months.september'),
            this.translateService.instant('months.october'),
            this.translateService.instant('months.november'),
            this.translateService.instant('months.december'),
        ];
        const back = year.slice(currentMonth + 1, year.length);
        const front = year.slice(0, currentMonth + 1);
        return back.concat(front);
    }

    private getLabelsForMonth(daysInMonth: number): string[] {
        const days: string[] = [];

        for (let i = 0; i < daysInMonth; i++) {
            days.push(
                dayjs()
                    .subtract(-this.currentPeriod, 'months')
                    .subtract(daysInMonth - 1 - i, 'days')
                    .format('DD.MM'),
            );
        }
        return days;
    }

    private getWeekdays(): string[] {
        const currentDay = dayjs().day();
        const days = [
            this.translateService.instant('weekdays.monday'),
            this.translateService.instant('weekdays.tuesday'),
            this.translateService.instant('weekdays.wednesday'),
            this.translateService.instant('weekdays.thursday'),
            this.translateService.instant('weekdays.friday'),
            this.translateService.instant('weekdays.saturday'),
            this.translateService.instant('weekdays.sunday'),
        ];
        const back = days.slice(currentDay, days.length);
        const front = days.slice(0, currentDay);
        return back.concat(front);
    }

    public switchTimeSpan(index: boolean): void {
        index ? (this.currentPeriod += 1) : (this.currentPeriod -= 1);
        this.initializeChart();
    }

    /**
     * Converts the data retrieved from the service to dedicated objects that can be interpreted by ngx-charts
     * and pushes them to ngxData.
     * Then, computes the upper limit for the y-axis of the chart.
     * @private
     */
    private pushToData(): void {
        this.ngxData = this.dataForSpanType.map((score, index) => ({ name: this.barChartLabels[index], value: score }));
        this.yScaleMax = Math.max(3, ...this.dataForSpanType);
    }

    /**
     * Handles the update of the data labels if the user changes the system language
     * @private
     */
    private onSystemLanguageChange(): void {
        this.createLabels();
        this.ngxData.forEach((dataPack, index) => {
            dataPack.name = this.barChartLabels[index];
        });
        this.ngxData = [...this.ngxData];
    }
}
