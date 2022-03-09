import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { Graphs } from 'app/entities/statistics.model';
import { TranslateService } from '@ngx-translate/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/entities/course.model';
import * as shape from 'd3-shape';
import { ActiveStudentsChartDirective } from 'app/shared/chart/active-students-chart.directive';

@Component({
    selector: 'jhi-course-management-overview-statistics',
    templateUrl: './course-management-overview-statistics.component.html',
    styleUrls: ['./course-management-overview-statistics.component.scss', '../detail/course-detail-line-chart.component.scss'],
})
export class CourseManagementOverviewStatisticsComponent extends ActiveStudentsChartDirective implements OnInit, OnChanges {
    @Input()
    amountOfStudentsInCourse: number;

    @Input()
    initialStats: number[] | undefined;
    @Input()
    course: Course;

    loading = true;
    graphType: Graphs = Graphs.ACTIVE_STUDENTS;

    // Data
    lineChartLabels: string[] = [];

    // ngx-data
    ngxData: any[] = [];
    chartColor: Color = {
        name: 'vivid',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: ['rgba(53,61,71,1)'], // color: black
    };
    curve: any = shape.curveMonotoneX;

    // Icons
    faSpinner = faSpinner;

    constructor(private translateService: TranslateService) {
        super();
    }

    ngOnInit() {
        this.translateService.onLangChange.subscribe(() => {
            this.updateTranslation();
        });
        this.determineDisplayedPeriod(this.course, 4);
        this.createChartLabels(this.currentOffsetToEndDate);
        this.createChartData();
    }

    ngOnChanges() {
        if (!!this.initialStats) {
            this.loading = false;
            this.createChartData();
        }
    }

    /**
     * Creates chart in order to visualize data provided by the inputs
     * @private
     */
    private createChartData(): void {
        const set: any[] = [];
        this.ngxData = [];
        if (this.amountOfStudentsInCourse > 0 && !!this.initialStats) {
            this.initialStats.forEach((value, index) => {
                if (index < this.currentSpanToStartDate) {
                    set.push({ name: this.lineChartLabels[index], value: (value * 100) / this.amountOfStudentsInCourse, absoluteValue: value });
                }
            });
        } else {
            this.lineChartLabels.forEach((label) => {
                set.push({ name: label, value: 0 });
            });
        }
        this.ngxData.push({ name: 'active students', series: set });
    }

    /**
     * Appends a percentage sign to every tick on the y axis
     * @param value the default tick
     */
    formatYAxis(value: any): string {
        return value.toLocaleString() + ' %';
    }

    /*private determineDisplayedPeriod() {
        const now = dayjs();
        if (this.course.startDate) {
            if (now.isBefore(this.course.startDate)) {
                this.startDateAlreadyPassed = false;
            } else if (determineDifferenceBetweenIsoWeeks(dayjs(this.course.startDate), now) < 3) {
                this.displayedTimeSpan = determineDifferenceBetweenIsoWeeks(dayjs(this.course.startDate), now) + 1;
            }
        } else if (this.course.endDate && now.isAfter(this.course.endDate) && determineDifferenceBetweenIsoWeeks(dayjs(this.course.endDate), now) > 0) {
            this.weekOffset = determineDifferenceBetweenIsoWeeks(dayjs(this.course.endDate), now);
        }

        this.createChartLabels(this.weekOffset);
    }*/

    private createChartLabels(weekOffset: number): void {
        for (let i = 0; i < this.currentSpanToStartDate; i++) {
            let translatePath: string;
            const week = Math.min(this.currentSpanToStartDate - 1, 3) - i + weekOffset;
            switch (week) {
                case 0: {
                    translatePath = 'overview.currentWeek';
                    break;
                }
                case 1: {
                    translatePath = 'overview.weekAgo';
                    break;
                }
                default: {
                    translatePath = 'overview.weeksAgo';
                }
            }
            this.lineChartLabels[i] = this.translateService.instant(translatePath, { amount: week });
        }
    }

    /**
     * Auxiliary method that ensures that the chart is translated directly when user selects a new language
     * @private
     */
    private updateTranslation(): void {
        this.createChartLabels(this.currentOffsetToEndDate);
        this.createChartData();
    }
}
