import { Component, effect, inject, input, signal, untracked } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { CurveFactory } from 'd3-shape';
import dayjs from 'dayjs/esm';
import { CourseManagementService } from '../services/course-management.service';
import { Color, LineChartModule, ScaleType } from '@swimlane/ngx-charts';
import { roundScorePercentSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { Course } from 'app/core/course/shared/entities/course.model';
import { faArrowLeft, faArrowRight, faSpinner } from '@fortawesome/free-solid-svg-icons';
import * as shape from 'd3-shape';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { mean } from 'simple-statistics';
import { RouterLink } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ActiveStudentsChart } from 'app/core/course/shared/entities/active-students-chart';

export enum SwitchTimeSpanDirection {
    LEFT,
    RIGHT,
}

@Component({
    selector: 'jhi-course-detail-line-chart',
    templateUrl: './course-detail-line-chart.component.html',
    styleUrls: ['./course-detail-line-chart.component.scss'],
    imports: [RouterLink, TranslateDirective, HelpIconComponent, NgbTooltip, FaIconComponent, LineChartModule, ArtemisDatePipe, ArtemisTranslatePipe],
})
export class CourseDetailLineChartComponent extends ActiveStudentsChart {
    private courseManagementService = inject(CourseManagementService);
    private translateService = inject(TranslateService);

    protected readonly SwitchTimeSpanDirection = SwitchTimeSpanDirection;

    readonly course = input.required<Course>();
    readonly numberOfStudentsInCourse = input.required<number>();

    readonly loading = signal(true);
    readonly displayedNumberOfWeeks = signal(8);
    readonly showsCurrentWeek = signal(true);

    // Chart related
    readonly amountOfStudents = signal('');
    readonly showLifetimeOverview = signal(false);
    readonly overviewStats = signal<number[] | undefined>(undefined);

    // Left arrow -> decrease, right arrow -> increase
    private currentPeriod = 0;

    // NGX variables
    chartColor: Color = {
        name: 'vivid',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [GraphColors.DARK_BLUE],
    };
    readonly xAxisLabel = signal('');

    readonly data = signal<any[]>([]);
    // Data changes will be stored in the copy first, to trigger change detection when ready
    dataCopy: { name: string; series: { name?: string; value?: number }[] }[] = [
        {
            name: '',
            series: [{}],
        },
    ];
    // Used for storing absolute values to display in tooltip
    absoluteSeries: { absoluteValue?: number; name?: string }[] = [{}];
    curve: CurveFactory = shape.curveMonotoneX;
    readonly average = signal({ name: 'Mean', value: 0 });
    readonly startDateDisplayed = signal(false);

    // Icons
    faSpinner = faSpinner;
    faArrowLeft = faArrowLeft;
    faArrowRight = faArrowRight;

    constructor() {
        super();

        // Effect to handle language changes
        effect(() => {
            // Subscribe to language changes is handled once in constructor
            untracked(() => {
                this.translateService.onLangChange.subscribe(() => {
                    this.loadTranslations();
                });
            });
        });

        // Effect to handle input changes (replaces ngOnChanges)
        effect(() => {
            const course = this.course();
            // Read the signal to track it but the value is used via this.numberOfStudentsInCourse() in processDataAndCreateChart
            this.numberOfStudentsInCourse();
            const displayedNumberOfWeeks = this.displayedNumberOfWeeks();

            untracked(() => {
                this.amountOfStudents.set(this.translateService.instant('artemisApp.courseStatistics.amountOfStudents'));
                this.loadTranslations();
                this.determineDisplayedPeriod(course, displayedNumberOfWeeks);
                /*
                if the course has a start date and already ended
                (i.e. the time difference between today and the course end date is at least one week), we show the lifetime overview by default.
                 */
                if (course.startDate && !!this.currentOffsetToEndDate) {
                    this.showLifetimeOverview.set(true);
                    this.displayLifetimeOverview();
                } else {
                    this.displayPeriodOverview(displayedNumberOfWeeks);
                }
            });
        });
    }

    /**
     * Reload the chart with the new data after an arrow is clicked
     */
    private reloadChart() {
        this.loading.set(true);
        this.createLabels();
        this.courseManagementService.getStatisticsData(this.course().id!, this.currentPeriod, this.displayedNumberOfWeeks()).subscribe((res: number[]) => {
            this.processDataAndCreateChart(res);
            this.data.set([...this.dataCopy]);
        });
    }

    /**
     * Takes the data, converts it into percentage and sets it accordingly
     */
    private processDataAndCreateChart(array: number[]) {
        let currentMean = 0;
        if (this.numberOfStudentsInCourse() > 0) {
            const allValues = [];
            for (let i = 0; i < array.length; i++) {
                const course = this.course();
                const numberOfStudentsInCourse = this.numberOfStudentsInCourse();
                allValues.push(roundScorePercentSpecifiedByCourseSettings(array[i] / numberOfStudentsInCourse, course));
                // Ensure the series element exists before setting properties
                if (this.dataCopy[0].series[i]) {
                    this.dataCopy[0].series[i]['value'] = roundScorePercentSpecifiedByCourseSettings(array[i] / numberOfStudentsInCourse, course);
                }
                if (this.absoluteSeries[i]) {
                    this.absoluteSeries[i]['absoluteValue'] = array[i];
                }
            }
            currentMean = allValues.length > 0 ? mean(allValues) : 0;
        } else {
            for (let i = 0; i < this.currentSpanSize; i++) {
                // Ensure the series element exists before setting properties
                if (this.dataCopy[0].series[i]) {
                    this.dataCopy[0].series[i]['value'] = 0;
                }
                if (this.absoluteSeries[i]) {
                    this.absoluteSeries[i]['absoluteValue'] = 0;
                }
            }
        }

        this.average.set({
            name: this.translateService.instant('artemisApp.courseStatistics.average') + currentMean.toFixed(2) + '%',
            value: currentMean,
        });
        this.loading.set(false);
    }

    private createLabels() {
        this.dataCopy[0].series = [{}];
        this.absoluteSeries = [{}];
        let endDate: dayjs.Dayjs;
        if (this.showLifetimeOverview()) {
            endDate = dayjs().subtract(this.currentOffsetToEndDate, 'weeks');
            this.currentSpanSize = this.determineDifferenceBetweenIsoWeeks(this.course().startDate!, endDate) + 1;
        } else {
            /*
                This variable contains the number of weeks between the last displayed week in the chart and the current date.
                If the end date is already passed, currentOffsetToEndDate represents the number of weeks between the course end date and the current date.
                displayedNumberOfWeeks determines the normal scope of the chart (usually 17 weeks).
                currentPeriod indicates how many times the observer shifted the scope in the past (by pressing the arrow)
             */
            const diffToLastChartWeek = this.currentOffsetToEndDate - this.displayedNumberOfWeeks() * this.currentPeriod;
            endDate = dayjs().subtract(diffToLastChartWeek, 'weeks');
            const course = this.course();
            const remainingWeeksTillStartDate = course.startDate ? this.determineDifferenceBetweenIsoWeeks(course.startDate, endDate) + 1 : this.displayedNumberOfWeeks();
            this.currentSpanSize = Math.min(remainingWeeksTillStartDate, this.displayedNumberOfWeeks());
            // for the start date, we subtract the currently possible span size - 1 from the end date in addition
            this.startDateDisplayed.set(!!this.course().startDate && remainingWeeksTillStartDate <= this.displayedNumberOfWeeks());
        }
        this.assignLabelsToDataObjects();
        this.dataCopy[0].name = this.amountOfStudents();
    }

    switchTimeSpan(direction: SwitchTimeSpanDirection): void {
        if (direction === SwitchTimeSpanDirection.RIGHT) {
            this.currentPeriod += 1;
        } else if (direction === SwitchTimeSpanDirection.LEFT) {
            this.currentPeriod -= 1;
        }
        this.showsCurrentWeek.set(this.currentPeriod === 0);
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
        this.displayedNumberOfWeeks.set(periodSize);
        this.showLifetimeOverview.set(false);
        this.loading.set(true);
        this.createLabels();
        this.reloadChart();
    }

    /**
     * Creates the chart for the lifetime overview
     */
    displayLifetimeOverview(): void {
        // if the course does not have a start date, we can't create an meaningful lifetime overview
        if (!this.course().startDate) {
            return;
        }
        this.showLifetimeOverview.set(true);
        this.loading.set(true);
        this.currentPeriod = 0;
        this.startDateDisplayed.set(true);
        this.showsCurrentWeek.set(true);
        this.createLabels();
        // we cache the overview stats to prevent many server requests
        const cachedOverviewStats = this.overviewStats();
        if (!cachedOverviewStats) {
            this.fetchLifetimeOverviewData();
        } else {
            this.processDataAndCreateChart(cachedOverviewStats);
            this.data.set([...this.dataCopy]);
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
                .subtract(this.currentOffsetToEndDate + this.currentSpanSize - 1 - this.displayedNumberOfWeeks() * this.currentPeriod - i, 'weeks')
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
        this.courseManagementService.getStatisticsForLifetimeOverview(this.course().id!).subscribe((res: number[]) => {
            this.overviewStats.set(res);
            this.processDataAndCreateChart(res);
            this.data.set([...this.dataCopy]);
        });
    }

    /**
     * Auxiliary method handles the translation sensitivity of the x axis label
     */
    private loadTranslations() {
        this.xAxisLabel.set(this.translateService.instant('artemisApp.courseStatistics.calendarWeek'));
        const currentAverage = this.average();
        this.average.set({
            name: this.translateService.instant('artemisApp.courseStatistics.average') + currentAverage.value.toFixed(2) + '%',
            value: currentAverage.value,
        });
    }
}
