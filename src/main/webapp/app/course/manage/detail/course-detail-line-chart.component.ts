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

export enum SwitchTimeSpanDirection {
    LEFT,
    RIGHT,
}

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
    loading = true;

    displayedNumberOfWeeks: number = 8;
    showsCurrentWeek = true;

    // Chart related
    chartTime: any;
    amountOfStudents: string;
    showLifetimeOverview = false;
    overviewStats: number[];

    // Left arrow -> decrease, right arrow -> increase
    private currentPeriod = 0;

    // NGX variables
    chartColor: Color = {
        name: 'vivid',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [GraphColors.DARK_BLUE],
    };
    xAxisLabel: string;

    data: any[];
    // Data changes will be stored in the copy first, to trigger change detection when ready
    dataCopy: { name: string; series: { name?: string; value?: number }[] }[] = [
        {
            name: '',
            series: [{}],
        },
    ];
    // Used for storing absolute values to display in tooltip
    absoluteSeries: { absoluteValue?: number; name?: string }[] = [{}];
    curve: any = shape.curveMonotoneX;
    average = { name: 'Mean', value: 0 };
    startDateDisplayed = false;

    // Icons
    faSpinner = faSpinner;
    faArrowLeft = faArrowLeft;
    faArrowRight = faArrowRight;

    constructor(
        private service: CourseManagementService,
        private translateService: TranslateService,
    ) {
        super();
        this.translateService.onLangChange.subscribe(() => {
            this.loadTranslations();
        });
    }

    ngOnChanges() {
        this.amountOfStudents = this.translateService.instant('artemisApp.courseStatistics.amountOfStudents');
        this.loadTranslations();
        this.determineDisplayedPeriod(this.course, this.displayedNumberOfWeeks);
        /*
        if the course has a start date and already ended
        (i.e. the time difference between today and the course end date is at least one week), we show the lifetime overview by default.
         */
        if (this.course.startDate && !!this.currentOffsetToEndDate) {
            this.showLifetimeOverview = true;
            this.displayLifetimeOverview();
        } else {
            this.displayPeriodOverview(this.displayedNumberOfWeeks);
        }
    }

    /**
     * Reload the chart with the new data after an arrow is clicked
     */
    private reloadChart() {
        this.loading = true;
        this.createLabels();
        this.service.getStatisticsData(this.course.id!, this.currentPeriod, this.displayedNumberOfWeeks).subscribe((res: number[]) => {
            this.processDataAndCreateChart(res);
            this.data = [...this.dataCopy];
        });
    }

    /**
     * Takes the data, converts it into percentage and sets it accordingly
     */
    private processDataAndCreateChart(array: number[]) {
        let currentMean = 0;
        if (this.numberOfStudentsInCourse > 0) {
            const allValues = [];
            for (let i = 0; i < array.length; i++) {
                allValues.push(roundScorePercentSpecifiedByCourseSettings(array[i] / this.numberOfStudentsInCourse, this.course));
                this.dataCopy[0].series[i]['value'] = roundScorePercentSpecifiedByCourseSettings(array[i] / this.numberOfStudentsInCourse, this.course); // allValues[i];
                this.absoluteSeries[i]['absoluteValue'] = array[i];
            }
            currentMean = allValues.length > 0 ? mean(allValues) : 0;
        } else {
            for (let i = 0; i < this.currentSpanSize; i++) {
                this.dataCopy[0].series[i]['value'] = 0;
                this.absoluteSeries[i]['absoluteValue'] = 0;
            }
        }

        this.average.name = this.translateService.instant('artemisApp.courseStatistics.average') + currentMean.toFixed(2) + '%';
        this.average.value = currentMean;
        this.loading = false;
    }

    private createLabels() {
        this.dataCopy[0].series = [{}];
        this.absoluteSeries = [{}];
        let startDate: dayjs.Dayjs;
        let endDate: dayjs.Dayjs;
        if (this.showLifetimeOverview) {
            startDate = this.course.startDate!;
            endDate = dayjs().subtract(this.currentOffsetToEndDate, 'weeks');
            this.currentSpanSize = this.determineDifferenceBetweenIsoWeeks(this.course.startDate!, endDate) + 1;
        } else {
            /*
        This variable contains the number of weeks between the last displayed week in the chart and the current date.
        If the end date is already passed, currentOffsetToEndDate represents the number of weeks between the course end date and the current date.
        displayedNumberOfWeeks determines the normal scope of the chart (usually 17 weeks).
        currentPeriod indicates how many times the observer shifted the scope in the past (by pressing the arrow)
         */
            const diffToLastChartWeek = this.currentOffsetToEndDate - this.displayedNumberOfWeeks * this.currentPeriod;
            endDate = dayjs().subtract(diffToLastChartWeek, 'weeks');
            const remainingWeeksTillStartDate = this.course.startDate ? this.determineDifferenceBetweenIsoWeeks(this.course.startDate, endDate) + 1 : this.displayedNumberOfWeeks;
            this.currentSpanSize = Math.min(remainingWeeksTillStartDate, this.displayedNumberOfWeeks);
            // for the start date, we subtract the currently possible span size - 1 from the end date in addition
            startDate = dayjs().subtract(diffToLastChartWeek + this.currentSpanSize - 1, 'weeks');
            this.startDateDisplayed = !!this.course.startDate && remainingWeeksTillStartDate <= this.displayedNumberOfWeeks;
        }
        this.assignLabelsToDataObjects();
        this.chartTime = startDate.isoWeekday(1).format('DD.MM.YYYY') + ' - ' + endDate.isoWeekday(7).format('DD.MM.YYYY');
        this.dataCopy[0].name = this.amountOfStudents;
    }

    switchTimeSpan(direction: SwitchTimeSpanDirection): void {
        if (direction === SwitchTimeSpanDirection.RIGHT) {
            this.currentPeriod += 1;
        } else if (direction === SwitchTimeSpanDirection.LEFT) {
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
     * Creates the chart for the default scope
     */
    displayPeriodOverview(periodSize: number): void {
        this.currentPeriod = 0;
        this.displayedNumberOfWeeks = periodSize;
        this.showLifetimeOverview = false;
        this.loading = true;
        this.createLabels();

        if (!this.initialStats) {
            this.reloadChart();
        } else {
            this.processDataAndCreateChart(this.initialStats.slice(Math.max(this.initialStats.length - this.displayedNumberOfWeeks, 0)));
            this.data = [...this.dataCopy];
        }
    }

    /**
     * Creates the chart for the lifetime overview
     */
    displayLifetimeOverview(): void {
        // if the course does not have a start date, we can't create an meaningful lifetime overview
        if (!this.course.startDate) {
            return;
        }
        this.showLifetimeOverview = true;
        this.loading = true;
        this.currentPeriod = 0;
        this.startDateDisplayed = true;
        this.showsCurrentWeek = true;
        this.createLabels();
        // we cache the overview stats to prevent many server requests
        if (!this.overviewStats) {
            this.fetchLifetimeOverviewData();
        } else {
            this.processDataAndCreateChart(this.overviewStats);
            this.data = [...this.dataCopy];
        }
    }

    /**
     * Auxiliary method reducing the complexity of {@link CourseDetailLineChartComponent#createLabels}
     * Assigns the correct calendar week numbers to the corresponding data objects as string
     * Note: the conversion to strings is important as ngx-charts increases the tick steps of on the x axis otherwise
     */
    private assignLabelsToDataObjects(): void {
        let currentWeek;
        for (let i = 0; i < this.currentSpanSize; i++) {
            currentWeek = dayjs()
                .subtract(this.currentOffsetToEndDate + this.currentSpanSize - 1 - this.displayedNumberOfWeeks * this.currentPeriod - i, 'weeks')
                .isoWeekday(1)
                .isoWeek();
            this.dataCopy[0].series[i] = {};
            this.dataCopy[0].series[i]['name'] = currentWeek.toString();
            this.absoluteSeries[i] = {};
            this.absoluteSeries[i]['name'] = currentWeek.toString();
        }
    }

    /**
     * Fetches and caches the data for the lifetime overview from the server and creates the chart
     */
    private fetchLifetimeOverviewData(): void {
        this.service.getStatisticsForLifetimeOverview(this.course.id!).subscribe((res: number[]) => {
            this.overviewStats = res;
            this.processDataAndCreateChart(this.overviewStats);
            this.data = [...this.dataCopy];
        });
    }

    /**
     * Auxiliary method handles the translation sensitivity of the x axis label
     */
    private loadTranslations() {
        this.xAxisLabel = this.translateService.instant('artemisApp.courseStatistics.calendarWeek');
        this.average.name = this.translateService.instant('artemisApp.courseStatistics.average') + this.average.value.toFixed(2) + '%';
    }

    protected readonly SwitchTimeSpanDirection = SwitchTimeSpanDirection;
}
