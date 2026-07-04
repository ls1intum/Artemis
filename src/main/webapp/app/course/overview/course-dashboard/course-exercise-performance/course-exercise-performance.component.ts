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

export interface ExercisePerformance {
    exerciseId: number;
    title: string;
    shortName?: string;
    score?: number;
    averageScore?: number;
}

const YOUR_GRAPH_COLOR = GraphColors.BLUE;
const AVERAGE_GRAPH_COLOR = GraphColors.YELLOW;

const MAX_TICK_LENGTH = 6;

@Component({
    selector: 'jhi-course-exercise-performance',
    templateUrl: './course-exercise-performance.component.html',
    styleUrls: ['./course-exercise-performance.component.scss'],
    imports: [TranslateDirective, HelpIconComponent, ChartModule],
})
export class CourseExercisePerformanceComponent {
    private translateService = inject(TranslateService);
    // Track language changes as a signal to make computed translations reactive
    private readonly langChange = toSignal(this.translateService.onLangChange);

    readonly exercisePerformance = input<ExercisePerformance[]>([]);

    protected readonly YOUR_GRAPH_COLOR = YOUR_GRAPH_COLOR;
    protected readonly AVERAGE_GRAPH_COLOR = AVERAGE_GRAPH_COLOR;

    private readonly chartColors = inject(ChartColorService).resolvedColors(() => [YOUR_GRAPH_COLOR, AVERAGE_GRAPH_COLOR]);

    readonly yourScoreLabel = computed(() => {
        this.langChange(); // Establish dependency on language changes
        return this.translateService.instant('artemisApp.courseStudentDashboard.exercisePerformance.yourScoreLabel');
    });
    readonly averageScoreLabel = computed(() => {
        this.langChange(); // Establish dependency on language changes
        return this.translateService.instant('artemisApp.courseStudentDashboard.exercisePerformance.averageScoreLabel');
    });

    readonly chartEntries = computed<ChartMultiSeriesEntry[]>(() => {
        return [
            {
                name: this.yourScoreLabel(),
                series: this.exercisePerformance().map((performance) => {
                    return {
                        name: performance.shortName?.toUpperCase() || performance.title,
                        value: round(performance.score || 0, 1),
                        title: performance.title,
                    };
                }),
            },
            {
                name: this.averageScoreLabel(),
                series: this.exercisePerformance().map((performance) => {
                    return {
                        name: performance.shortName?.toUpperCase() || performance.title,
                        value: round(performance.averageScore || 0, 1),
                        title: performance.title,
                    };
                }),
            },
        ];
    });

    readonly yScaleMax = computed(() => {
        const data = this.chartEntries();
        const maxScore = Math.max(...data.flatMap((d) => d.series.map((series) => series.value)));
        return Math.max(100, Math.ceil(maxScore / 10) * 10);
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
                label: this.translateService.instant('artemisApp.courseStudentDashboard.exercisePerformance.xAxisLabel'),
                tickFormatter: (value) => {
                    const label = `${value}`;
                    return label.length > MAX_TICK_LENGTH ? label.slice(0, MAX_TICK_LENGTH) + '...' : label;
                },
            },
            yAxis: {
                label: this.translateService.instant('artemisApp.courseStudentDashboard.exercisePerformance.yAxisLabel'),
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
