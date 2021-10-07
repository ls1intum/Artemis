import { Component, Input, OnChanges } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs';
import { CourseManagementService } from '../course-management.service';
import { round } from 'app/shared/util/utils';
import { Color, ScaleType } from '@swimlane/ngx-charts';

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

    // Chart related
    chartTime: any;
    amountOfStudents: string;

    // Left arrow -> decrease, right arrow -> increase
    private currentPeriod = 0;

    // NGX variables
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
    timeline = false;
    data: any[];
    // Data changes will be stored in the copy first, to trigger change detection when ready
    dataCopy = [
        {
            name: '',
            series: [{}],
        },
    ];
    // Used for storing absolute values to display in tooltip
    absoluteSeries = [{}];

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
        this.data = this.dataCopy;
    }

    /**
     * Reload the chart with the new data after an arrow is clicked
     */
    private reloadChart() {
        this.loading = true;
        this.createLabels();
        this.service.getStatisticsData(this.courseId, this.currentPeriod).subscribe((res: number[]) => {
            this.processDataAndCreateChart(res);
            this.data = [...this.dataCopy];
        });
    }

    /**
     * Takes the data, converts it into percentage and sets it accordingly
     */
    private processDataAndCreateChart(array: number[]) {
        if (this.numberOfStudentsInCourse > 0) {
            for (let i = 0; i < array.length; i++) {
                this.dataCopy[0].series[i]['value'] = round((array[i] / this.numberOfStudentsInCourse) * 100);
                this.absoluteSeries[i]['absoluteValue'] = array[i];
            }
        } else {
            for (let i = 0; i < this.displayedNumberOfWeeks; i++) {
                this.dataCopy[0].series[i]['value'] = 0;
                this.absoluteSeries[i]['absoluteValue'] = 0;
            }
        }
        this.loading = false;
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
            this.dataCopy[0].series[i] = {};
            this.dataCopy[0].series[i]['name'] = prefix + ' ' + currentWeek;
            this.absoluteSeries[i] = {};
            this.absoluteSeries[i]['name'] = prefix + ' ' + currentWeek;
        }
        this.chartTime = startDate.isoWeekday(1).format('DD.MM.YYYY') + ' - ' + endDate.isoWeekday(7).format('DD.MM.YYYY');
        this.dataCopy[0].name = this.amountOfStudents;
    }

    switchTimeSpan(index: boolean): void {
        // eslint-disable-next-line chai-friendly/no-unused-expressions
        index ? (this.currentPeriod += 1) : (this.currentPeriod -= 1);
        this.reloadChart();
    }

    formatYAxis(value: any) {
        return value.toLocaleString() + ' %';
    }

    /**
     * Using the model, we look for the entry with the same title (CW XX) and return the absolute value for this entry
     */
    findAbsoluteValue(model: any) {
        const result: any = this.absoluteSeries.find((entry: any) => entry.name === model.name);
        return result ? result.absoluteValue : '/';
    }
}
