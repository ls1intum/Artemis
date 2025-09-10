import { Component, Input, OnChanges, OnInit, inject } from '@angular/core';
import { GraphColors, Graphs } from 'app/exercise/shared/entities/statistics.model';
import { TranslateService } from '@ngx-translate/core';
import { Color, LineChartModule, ScaleType } from '@swimlane/ngx-charts';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CurveFactory } from 'd3-shape';
import * as shape from 'd3-shape';
import { RouterLink } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ActiveStudentsChart } from 'app/core/course/shared/entities/active-students-chart';

@Component({
    selector: 'jhi-course-management-overview-statistics',
    templateUrl: './course-management-overview-statistics.component.html',
    styleUrls: ['./course-management-overview-statistics.component.scss', '../detail/course-detail-line-chart.component.scss'],
    imports: [RouterLink, TranslateDirective, HelpIconComponent, LineChartModule, ArtemisDatePipe],
})
export class CourseManagementOverviewStatisticsComponent extends ActiveStudentsChart implements OnInit, OnChanges {
    private translateService = inject(TranslateService);

    @Input() amountOfStudentsInCourse: number;
    @Input() initialStats: number[] | undefined;
    @Input() course: Course;

    graphType: Graphs = Graphs.ACTIVE_STUDENTS;

    // Data
    lineChartLabels: string[] = [];

    // ngx-data
    ngxData: any[] = [];
    chartColor: Color = {
        name: 'vivid',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [GraphColors.BLACK],
    };
    curve: CurveFactory = shape.curveMonotoneX;

    // Icons
    faSpinner = faSpinner;

    ngOnInit() {
        this.translateService.onLangChange.subscribe(() => {
            this.updateTranslation();
        });
        this.determineDisplayedPeriod(this.course, 4);
        this.createChartLabels(this.currentOffsetToEndDate);
        this.createChartData();
    }

    ngOnChanges() {
        if (this.initialStats) {
            this.createChartData();
        }
    }

    /**
     * Creates chart in order to visualize data provided by the inputs
     */
    private createChartData(): void {
        const set: any[] = [];
        this.ngxData = [];
        if (this.amountOfStudentsInCourse > 0 && !!this.initialStats) {
            this.initialStats.forEach((absoluteValue, index) => {
                set.push({
                    name: this.lineChartLabels[index],
                    value: (absoluteValue * 100) / this.amountOfStudentsInCourse,
                    absoluteValue,
                });
            });
        } else {
            this.lineChartLabels.forEach((label) => {
                set.push({ name: label, value: 0, absoluteValue: 0 });
            });
        }
        this.ngxData.push({ name: 'active students', series: set });
    }

    /**
     * Appends a percentage sign to every tick on the y-axis
     * @param value the default tick
     */
    formatYAxis(value: any): string {
        return value.toLocaleString() + ' %';
    }

    private createChartLabels(weekOffset: number): void {
        for (let i = 0; i < this.currentSpanSize; i++) {
            let translatePath: string;
            const week = Math.min(this.currentSpanSize - 1, 3) - i + weekOffset;
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
     */
    private updateTranslation(): void {
        this.createChartLabels(this.currentOffsetToEndDate);
        this.createChartData();
    }
}
