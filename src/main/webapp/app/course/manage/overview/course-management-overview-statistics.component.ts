import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { Graphs } from 'app/entities/statistics.model';
import { TranslateService } from '@ngx-translate/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/entities/course.model';
import * as shape from 'd3-shape';
import dayjs from 'dayjs/esm';

@Component({
    selector: 'jhi-course-management-overview-statistics',
    templateUrl: './course-management-overview-statistics.component.html',
    styleUrls: ['./course-management-overview-statistics.component.scss', '../detail/course-detail-line-chart.component.scss'],
})
export class CourseManagementOverviewStatisticsComponent implements OnInit, OnChanges {
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

    constructor(private translateService: TranslateService) {}

    ngOnInit() {
        this.translateService.onLangChange.subscribe(() => {
            this.updateTranslation();
        });

        this.createChartLabels();
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
                set.push({ name: this.lineChartLabels[index], value: (value * 100) / this.amountOfStudentsInCourse, absoluteValue: value });
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

    private createChartLabels(): void {
        let weekOffset = 0;
        const now = dayjs();
        // If the course end date is passed, the server returns the data until the end date. We have to adapt the labels accordingly
        if (this.course.endDate && now.isAfter(this.course.endDate) && now.isoWeek() !== dayjs(this.course.endDate).isoWeek()) {
            const endDateIsoWeek = dayjs(this.course.endDate).isoWeek();
            if (endDateIsoWeek > now.isoWeek()) {
                weekOffset = dayjs(this.course.endDate).isoWeeksInYear() - endDateIsoWeek + now.isoWeek();
            } else {
                weekOffset = now.isoWeek() - endDateIsoWeek;
            }
        }
        for (let i = 0; i < 4; i++) {
            let translatePath: string;
            const week = 3 - i + weekOffset;
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
        this.createChartLabels();
        this.createChartData();
    }
}
