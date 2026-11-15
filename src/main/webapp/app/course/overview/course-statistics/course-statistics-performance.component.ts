import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslateService } from '@ngx-translate/core';
import {
    TumAetUiButtonComponent,
    TumAetUiChartSelectEvent,
    TumAetUiChartSeries,
    TumAetUiCheckboxComponent,
    TumAetUiLineChartComponent,
    TumAetUiLineChartConfig,
    TumAetUiMessageComponent,
} from '@tumaet/ui-angular';
import { Router } from '@angular/router';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CourseStatisticsService, ExercisePeerComparison } from './course-statistics.service';

@Component({
    selector: 'jhi-course-statistics-performance',
    templateUrl: './course-statistics-performance.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, ArtemisTranslatePipe, TumAetUiButtonComponent, TumAetUiCheckboxComponent, TumAetUiLineChartComponent, TumAetUiMessageComponent],
})
export class CourseStatisticsPerformanceComponent {
    private readonly statistics = inject(CourseStatisticsService);
    private readonly translateService = inject(TranslateService);
    private readonly router = inject(Router);
    private readonly language = toSignal(this.translateService.onLangChange);

    readonly courseId = input.required<number>();
    readonly refresh = input(0);
    readonly excludedExerciseIds = input<number[]>([]);
    readonly showComparison = signal(true);
    readonly loading = signal(true);
    readonly failed = signal(false);
    readonly response = signal<ExercisePeerComparison | undefined>(undefined);
    private readonly retry = signal(0);

    readonly exercises = computed(() =>
        (this.response()?.exercises ?? [])
            .filter((exercise) => !this.excludedExerciseIds().includes(exercise.exerciseId))
            .sort((a, b) => (a.releaseDate ?? '').localeCompare(b.releaseDate ?? '') || a.exerciseId - b.exerciseId),
    );
    readonly hasComparison = computed(() => this.exercises().some((exercise) => exercise.peerAverage !== undefined));
    readonly chartLabels = computed(() => this.exercises().map((exercise) => exercise.exerciseTitle ?? `${exercise.exerciseId}`));
    readonly chartWidth = computed(() => Math.max(550, this.exercises().length * 72));
    readonly chartSeries = computed<TumAetUiChartSeries[]>(() => {
        this.language();
        const exercises = this.exercises();
        const series: TumAetUiChartSeries[] = [
            {
                label: this.translateService.instant('artemisApp.exercise-scores-chart.yourScoreLabel'),
                data: exercises.map((entry) => entry.ownScore),
                color: 'var(--tumaet-ui-primary-color)',
                meta: exercises,
            },
        ];
        if (this.showComparison() && this.hasComparison()) {
            series.push({
                label: this.translateService.instant('artemisApp.courseOverview.statistics.redesign.similarStudents'),
                data: exercises.map((entry) => entry.peerAverage),
                color: 'var(--tumaet-ui-muted-color)',
                meta: exercises,
            });
        }
        return series;
    });
    readonly chartConfig = computed<TumAetUiLineChartConfig>(() => ({
        legend: { position: 'bottom' },
        spanGaps: false,
        yAxis: { min: 0, max: Math.max(100, ...this.exercises().flatMap((entry) => [entry.ownScore ?? 0, entry.peerAverage ?? 0])), tickFormatter: (value) => `${value}%` },
        xAxis: { tickFormatter: (value) => (`${value}`.length > 18 ? `${`${value}`.slice(0, 18)}…` : `${value}`) },
        tooltip: { label: (entry) => `${entry.seriesLabel}: ${entry.value}%` },
    }));

    constructor() {
        effect((cleanup) => {
            const courseId = this.courseId();
            this.refresh();
            this.retry();
            this.loading.set(true);
            this.failed.set(false);
            this.response.set(undefined);
            const subscription = this.statistics.getPeerComparison(courseId).subscribe({
                next: (response) => {
                    this.response.set(response);
                    this.loading.set(false);
                },
                error: () => {
                    this.failed.set(true);
                    this.loading.set(false);
                },
            });
            cleanup(() => subscription.unsubscribe());
        });
    }

    reload(): void {
        this.retry.update((value) => value + 1);
    }

    onSelect(event: TumAetUiChartSelectEvent): void {
        const exercise = this.exercises()[event.index];
        if (exercise) {
            void this.router.navigate(['/courses', this.courseId(), 'exercises', exercise.exerciseId]);
        }
    }
}
