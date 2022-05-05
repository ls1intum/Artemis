import { Component, Input, OnChanges } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import dayjs from 'dayjs/esm';
import { CourseManagementService } from '../course-management.service';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { roundScorePercentSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { Course } from 'app/entities/course.model';
import { faArrowLeft, faArrowRight, faSpinner } from '@fortawesome/free-solid-svg-icons';
import * as shape from 'd3-shape';
import { GraphColors } from 'app/entities/statistics.model';
import { ActiveStudentsChart } from 'app/shared/chart/active-students-chart';
import { mean } from 'simple-statistics';

@Component({
    selector: 'jhi-course-detail-line-chart',
    templateUrl: './course-detail-line-chart.component.html',
    styleUrls: ['./course-detail-line-chart.component.scss'],
})
export class CourseDetailLineChartComponent extends ActiveStudentsChart implements OnChanges {
    @Input()
    course: Course;
    @Input()
    numberOfStudentsInCourse: number;
    @Input()
    initialStats: number[] | undefined;
    initialStatsReceived = false;
    loading = true;

    LEFT = false;
    RIGHT = true;
    readonly displayedNumberOfWeeks = 17;
    showsCurrentWeek = true;

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
        domain: [GraphColors.DARK_BLUE],
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
    curve: any = shape.curveMonotoneX;
    average = { name: 'Average', value: 0 };
    showAverage = true;
    startDateDisplayed = false;

    // Icons
    faSpinner = faSpinner;
    faArrowLeft = faArrowLeft;
    faArrowRight = faArrowRight;

    constructor(private service: CourseManagementService, private translateService: TranslateService) {
        super();
    }

    ngOnChanges() {
        this.loading = true;
        this.amountOfStudents = this.translateService.instant('artemisApp.courseStatistics.amountOfStudents');
        // Only use the pre-loaded stats once
        if (this.initialStatsReceived || !this.initialStats) {
            return;
        }
        this.initialStatsReceived = true;
        this.determineDisplayedPeriod(this.course, this.displayedNumberOfWeeks);
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
        this.service.getStatisticsData(this.course.id!, this.currentPeriod).subscribe((res: number[]) => {
            this.processDataAndCreateChart(res);
            this.data = [...this.dataCopy];
        });
    }

    /**
     * Takes the data, converts it into percentage and sets it accordingly
     */
    private processDataAndCreateChart(array: number[]) {
        if (this.numberOfStudentsInCourse > 0) {
            const allValues = [];
            for (let i = 0; i < array.length; i++) {
                allValues.push(roundScorePercentSpecifiedByCourseSettings(array[i] / this.numberOfStudentsInCourse, this.course));
                this.dataCopy[0].series[i]['value'] = roundScorePercentSpecifiedByCourseSettings(array[i] / this.numberOfStudentsInCourse, this.course); // allValues[i];
                this.absoluteSeries[i]['absoluteValue'] = array[i];
            }
            const currentAverage = allValues.length > 0 ? mean(allValues) : 0;
            this.average.name = currentAverage.toFixed(2) + '%';
            this.average.value = currentAverage;
        } else {
            for (let i = 0; i < this.displayedNumberOfWeeks; i++) {
                this.dataCopy[0].series[i]['value'] = 0;
                this.absoluteSeries[i]['absoluteValue'] = 0;
            }
        }
        this.loading = false;
    }

    private createLabels() {
        this.dataCopy[0].series = [{}];
        this.absoluteSeries = [{}];
        const prefix = this.translateService.instant('calendar_week');
        /*
        This variable contains the number of weeks between the last displayed week in the chart and the current date.
        If the end date is already passed, currentOffsetToEndDate represents the number of weeks between the course end date and the current date.
        displayedNumberOfWeeks determines the normal scope of the chart (usually 17 weeks).
        currentPeriod indicates how many times the observer shifted the scope in the past (by pressing the arrow)
         */
        const diffToLastChartWeek = this.currentOffsetToEndDate - this.displayedNumberOfWeeks * this.currentPeriod;
        const endDate = dayjs().subtract(diffToLastChartWeek, 'weeks');
        const remainingWeeksTillStartDate = this.course.startDate ? this.determineDifferenceBetweenIsoWeeks(this.course.startDate, endDate) + 1 : this.displayedNumberOfWeeks;
        this.currentSpanSize = Math.min(remainingWeeksTillStartDate, this.displayedNumberOfWeeks);
        // for the start date, we subtract the currently possible span size - 1 from the end date in addition
        const startDate = dayjs().subtract(diffToLastChartWeek + this.currentSpanSize - 1, 'weeks');
        this.startDateDisplayed = !!this.course.startDate && remainingWeeksTillStartDate <= this.displayedNumberOfWeeks;
        let currentWeek;
        for (let i = 0; i < this.currentSpanSize; i++) {
            currentWeek = dayjs()
                .subtract(this.currentOffsetToEndDate + this.currentSpanSize - 1 - this.displayedNumberOfWeeks * this.currentPeriod - i, 'weeks')
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
        if (index) {
            this.currentPeriod += 1;
        } else {
            this.currentPeriod -= 1;
        }
        this.showsCurrentWeek = this.currentPeriod === 0;
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

    /**
     * Switches the visibility state for the reference line in the chart
     */
    toggleAverageLine(): void {
        this.showAverage = !this.showAverage;
    }
}
