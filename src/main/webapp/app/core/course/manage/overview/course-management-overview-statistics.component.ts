import { Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
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
export class CourseManagementOverviewStatisticsComponent extends ActiveStudentsChart {
    private readonly translateService = inject(TranslateService);
    private readonly destroyRef = inject(DestroyRef);

    readonly amountOfStudentsInCourse = input.required<number>();
    readonly initialStats = input<number[]>();
    readonly course = input.required<Course>();

    readonly graphType: Graphs = Graphs.ACTIVE_STUDENTS;

    // Data
    readonly lineChartLabels = signal<string[]>([]);

    // ngx-data
    readonly ngxData = signal<any[]>([]);
    readonly chartColor: Color = {
        name: 'vivid',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [GraphColors.BLACK],
    };
    readonly curve: CurveFactory = shape.curveMonotoneX;

    // Icons
    readonly faSpinner = faSpinner;

    private initialized = false;

    constructor() {
        super();

        // Subscribe to language changes with proper cleanup
        this.translateService.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            this.updateTranslation();
        });

        // Effect for initialization based on course
        effect(() => {
            const course = this.course();
            if (!this.initialized && course) {
                this.initialized = true;
                this.determineDisplayedPeriod(course, 4);
                this.createChartLabels(this.currentOffsetToEndDate);
                untracked(() => this.createChartData());
            }
        });

        // Effect for initialStats changes
        effect(() => {
            const stats = this.initialStats();
            if (stats && this.initialized) {
                untracked(() => this.createChartData());
            }
        });
    }

    /**
     * Creates chart in order to visualize data provided by the inputs
     */
    private createChartData(): void {
        const set: any[] = [];
        const initialStats = this.initialStats();
        const labels = this.lineChartLabels();
        if (this.amountOfStudentsInCourse() > 0 && !!initialStats) {
            initialStats.forEach((absoluteValue, index) => {
                set.push({
                    name: labels[index],
                    value: (absoluteValue * 100) / this.amountOfStudentsInCourse(),
                    absoluteValue,
                });
            });
        } else {
            labels.forEach((label) => {
                set.push({ name: label, value: 0, absoluteValue: 0 });
            });
        }
        this.ngxData.set([{ name: 'active students', series: set }]);
    }

    /**
     * Appends a percentage sign to every tick on the y-axis
     * @param value the default tick
     */
    formatYAxis(value: any): string {
        return value.toLocaleString() + ' %';
    }

    private createChartLabels(weekOffset: number): void {
        const labels: string[] = [];
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
            labels[i] = this.translateService.instant(translatePath, { amount: week });
        }
        this.lineChartLabels.set(labels);
    }

    /**
     * Auxiliary method that ensures that the chart is translated directly when user selects a new language
     */
    private updateTranslation(): void {
        this.createChartLabels(this.currentOffsetToEndDate);
        this.createChartData();
    }
}
