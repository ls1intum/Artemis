import { Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { GraphColors, Graphs } from 'app/exercise/shared/entities/statistics.model';
import { TranslateService } from '@ngx-translate/core';
import { ChartModule } from 'primeng/chart';
import { faSpinner } from '@fortawesome/free-solid-svg-icons';
import { Course } from 'app/course/shared/entities/course.model';
import { ChartMultiSeriesEntry } from 'app/shared-ui/chart/chart-data.model';
import { ChartColorService } from 'app/shared-ui/chart/chart-color.service';
import { multiSeriesToLineData } from 'app/shared-ui/chart/chart-adapters';
import { lineChartOptions } from 'app/shared-ui/chart/chart-options';
import { RouterLink } from '@angular/router';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ActiveStudentsChart } from 'app/course/shared/entities/active-students-chart';

@Component({
    selector: 'jhi-course-management-overview-statistics',
    templateUrl: './course-management-overview-statistics.component.html',
    styleUrls: ['./course-management-overview-statistics.component.scss', '../detail/course-detail-line-chart.component.scss'],
    imports: [RouterLink, TranslateDirective, HelpIconComponent, ChartModule, ArtemisDatePipe],
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

    readonly chartEntries = signal<ChartMultiSeriesEntry[]>([]);

    private readonly chartColors = inject(ChartColorService).resolvedColors(() => [GraphColors.BLACK]);

    readonly chartData = computed(() => multiSeriesToLineData(this.chartEntries(), this.chartColors(), { monotone: true }));
    readonly chartOptions = computed(() =>
        lineChartOptions({
            yAxis: { min: 0, max: 100, tickFormatter: this.formatYAxis },
            tooltip: {
                label: (item) => {
                    const absoluteValue = item.dataset.meta?.[item.dataIndex]?.['absoluteValue'] ?? 0;
                    return this.translateService.instant('artemisApp.course.activeStudents', { students: absoluteValue });
                },
            },
        }),
    );

    // Icons
    readonly faSpinner = faSpinner;

    private readonly initialized = signal(false);

    constructor() {
        super();

        // Subscribe to language changes with proper cleanup
        this.translateService.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            this.updateTranslation();
        });

        // Effect for initialization based on course
        effect(() => {
            const course = this.course();
            if (!this.initialized() && course) {
                this.initialized.set(true);
                this.determineDisplayedPeriod(course, 4);
                this.createChartLabels(this.currentOffsetToEndDate);
                untracked(() => this.createChartData());
            }
        });

        // Effect for initialStats changes
        effect(() => {
            const stats = this.initialStats();
            if (stats && this.initialized()) {
                untracked(() => this.createChartData());
            }
        });
    }

    /**
     * Creates chart in order to visualize data provided by the inputs
     */
    private createChartData(): void {
        const set: ChartMultiSeriesEntry['series'] = [];
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
        this.chartEntries.set([{ name: 'active students', series: set }]);
    }

    /**
     * Appends a percentage sign to every tick on the y-axis
     * @param value the default tick
     */
    formatYAxis(value: number | string): string {
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
