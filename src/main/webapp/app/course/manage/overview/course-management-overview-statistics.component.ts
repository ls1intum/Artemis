import { Component, Input, OnChanges, OnDestroy, OnInit } from '@angular/core';
import { getGraphColorForTheme, GraphColors, Graphs } from 'app/entities/statistics.model';
import { TranslateService } from '@ngx-translate/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/entities/course.model';
import * as shape from 'd3-shape';
import { ThemeService } from 'app/core/theme/theme.service';
import { map, Subscription } from 'rxjs';

@Component({
    selector: 'jhi-course-management-overview-statistics',
    templateUrl: './course-management-overview-statistics.component.html',
    styleUrls: ['./course-management-overview-statistics.component.scss', '../detail/course-detail-line-chart.component.scss'],
})
export class CourseManagementOverviewStatisticsComponent implements OnInit, OnChanges, OnDestroy {
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
        domain: [],
    };
    curve: any = shape.curveMonotoneX;

    themeSubscription: Subscription;

    // Icons
    faSpinner = faSpinner;

    constructor(private translateService: TranslateService, private themeService: ThemeService) {}

    ngOnInit() {
        this.themeSubscription = this.themeService
            .getCurrentThemeObservable()
            .pipe(map((theme) => getGraphColorForTheme(theme, GraphColors.BLACK)))
            .subscribe((color) => (this.chartColor = { ...this.chartColor, domain: [color] }));

        for (let i = 0; i < 4; i++) {
            this.lineChartLabels[i] = this.translateService.instant(`overview.${3 - i}_weeks_ago`);
        }
    }

    ngOnChanges() {
        if (!!this.initialStats) {
            this.loading = false;
            this.createChartData();
        }
    }

    ngOnDestroy(): void {
        this.themeSubscription?.unsubscribe();
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
}
