import { Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import { ChartModule } from 'primeng/chart';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { ChartMultiSeriesEntry } from 'app/shared-ui/chart/chart-data.model';
import { ChartColorService } from 'app/shared-ui/chart/chart-color.service';
import { multiSeriesToLineData } from 'app/shared-ui/chart/chart-adapters';
import { lineChartOptions } from 'app/shared-ui/chart/chart-options';
import { round } from 'app/foundation/util/utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';

export interface ExerciseLateness {
    exerciseId: number;
    title: string;
    shortName?: string;
    relativeLatestSubmission?: number;
    relativeAverageLatestSubmission?: number;
}

const YOUR_GRAPH_COLOR = GraphColors.BLUE;
const AVERAGE_GRAPH_COLOR = GraphColors.YELLOW;

const MAX_TICK_LENGTH = 6;

@Component({
    selector: 'jhi-course-exercise-lateness',
    templateUrl: './course-exercise-lateness.component.html',
    styleUrls: ['./course-exercise-lateness.component.scss'],
    imports: [TranslateDirective, HelpIconComponent, ChartModule],
})
export class CourseExerciseLatenessComponent {
    private translateService = inject(TranslateService);
    // Track language changes as a signal to make computed translations reactive
    private readonly langChange = toSignal(this.translateService.onLangChange);

    readonly exerciseLateness = input<ExerciseLateness[]>([]);

    protected readonly YOUR_GRAPH_COLOR = YOUR_GRAPH_COLOR;
    protected readonly AVERAGE_GRAPH_COLOR = AVERAGE_GRAPH_COLOR;

    private readonly chartColors = inject(ChartColorService).resolvedColors(() => [YOUR_GRAPH_COLOR, AVERAGE_GRAPH_COLOR]);

    readonly yourLatenessLabel = computed(() => {
        this.langChange(); // Establish dependency on language changes
        return this.translateService.instant('artemisApp.courseStudentDashboard.exerciseLateness.yourLatenessLabel');
    });
    readonly averageLatenessLabel = computed(() => {
        this.langChange(); // Establish dependency on language changes
        return this.translateService.instant('artemisApp.courseStudentDashboard.exerciseLateness.averageLatenessLabel');
    });

    readonly chartEntries = computed<ChartMultiSeriesEntry[]>(() => {
        return [
            {
                name: this.yourLatenessLabel(),
                series: this.exerciseLateness().map((lateness) => {
                    return {
                        name: lateness.shortName?.toUpperCase() || lateness.title,
                        value: round(lateness.relativeLatestSubmission || 100, 1),
                        title: lateness.title,
                    };
                }),
            },
            {
                name: this.averageLatenessLabel(),
                series: this.exerciseLateness().map((lateness) => {
                    return {
                        name: lateness.shortName?.toUpperCase() || lateness.title,
                        value: round(lateness.relativeAverageLatestSubmission || 100, 1),
                        title: lateness.title,
                    };
                }),
            },
        ];
    });

    readonly yScaleMax = computed(() => {
        const data = this.chartEntries();
        const maxRelativeTime = Math.max(...data.flatMap((d) => d.series.map((series) => series.value)));
        return Math.max(100, Math.ceil(maxRelativeTime / 10) * 10);
    });

    readonly isDataAvailable = computed(() => {
        const data = this.chartEntries();
        return data && data.length > 0 && data.some((d) => d.series.length > 0);
    });

    readonly chartData = computed(() => multiSeriesToLineData(this.chartEntries(), this.chartColors()));
    readonly chartOptions = computed(() => {
        this.langChange(); // Establish dependency on language changes
        return lineChartOptions({
            legend: false,
            xAxis: {
                label: this.translateService.instant('artemisApp.courseStudentDashboard.exerciseLateness.xAxisLabel'),
                tickFormatter: (value) => {
                    const label = `${value}`;
                    return label.length > MAX_TICK_LENGTH ? label.slice(0, MAX_TICK_LENGTH) + '...' : label;
                },
            },
            yAxis: {
                label: this.translateService.instant('artemisApp.courseStudentDashboard.exerciseLateness.yAxisLabel'),
                min: 0,
                max: this.yScaleMax(),
            },
            tooltip: {
                title: (items) => (items[0]?.dataset.meta?.[items[0].dataIndex]?.['title'] as string | undefined) ?? '',
                label: (item) => `${item.dataset.label}: ${item.parsed.y}%`,
            },
        });
    });
}
