import { AfterViewInit, Component, DestroyRef, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ExerciseScoresChartService, ExerciseScoresDTO } from 'app/course/overview/visualizations/exercise-scores-chart.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { onError } from 'app/foundation/util/global.utils';
import { finalize } from 'rxjs/operators';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { sortBy } from 'lodash-es';
import { round } from 'app/foundation/util/utils';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faFilter } from '@fortawesome/free-solid-svg-icons';
import { ChartExerciseTypeFilter } from 'app/exercise/chart/chart-exercise-type-filter';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { ChartMultiSeriesEntry, ChartSeriesEntry } from 'app/shared-ui/chart/chart-data.model';
import { multiSeriesLineChart } from 'app/shared-ui/chart/tum-ui-chart-adapters';
import { TumUiChartSelectEvent, TumUiLineChartComponent, TumUiLineChartConfig } from '@tumaet/ui-angular';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

const MAX_TICK_LENGTH = 15;
// horizontal space reserved per exercise so that x-axis labels stay readable; with many exercises the chart grows and scrolls horizontally
const PIXELS_PER_EXERCISE = 100;

@Component({
    selector: 'jhi-exercise-scores-chart',
    templateUrl: './exercise-scores-chart.component.html',
    styleUrls: ['./exercise-scores-chart.component.scss'],
    imports: [TranslateDirective, NgbDropdown, NgbDropdownToggle, FaIconComponent, NgbDropdownMenu, TumUiLineChartComponent, ArtemisTranslatePipe],
})
export class ExerciseScoresChartComponent implements AfterViewInit {
    private navigationUtilService = inject(ArtemisNavigationUtilService);
    private activatedRoute = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private exerciseScoresChartService = inject(ExerciseScoresChartService);
    exerciseTypeFilter = inject(ChartExerciseTypeFilter);
    private translateService = inject(TranslateService);
    private destroyRef = inject(DestroyRef);

    readonly filteredExerciseIDs = input.required<number[]>();

    courseId!: number; // set in ngAfterViewInit() from the route params subscription before the chart data is loaded
    isLoading = false;
    exerciseScores: ExerciseScoresDTO[] = [];
    excludedExerciseScores: ExerciseScoresDTO[] = [];
    visibleExerciseScores: ExerciseScoresDTO[] = [];

    readonly ExerciseType = ExerciseType;
    readonly convertToMapKey = ChartExerciseTypeFilter.convertToMapKey;

    // Icons
    faFilter = faFilter;

    // chart data: your score, average score, and best score series
    readonly chartEntries = signal<ChartMultiSeriesEntry[]>([]);
    readonly xAxisLabel = signal('');
    readonly yAxisLabel = signal('');
    yourScoreLabel = '';
    averageScoreLabel = '';
    maximumScoreLabel = '';
    readonly maxScale = signal(101);

    readonly chartData = computed(() => multiSeriesLineChart(this.chartEntries(), [GraphColors.BLUE, GraphColors.YELLOW, GraphColors.GREEN]));
    // dynamic width so that the chart grows with the number of exercises and can be scrolled horizontally (CSS enforces the container width as minimum)
    readonly chartWidth = computed(() => (this.chartEntries()[0]?.series.length ?? 0) * PIXELS_PER_EXERCISE);
    readonly chartConfig = computed<TumUiLineChartConfig>(() => ({
        legend: { position: 'right' },
        xAxis: {
            label: this.xAxisLabel(),
            tickFormatter: (value) => {
                const label = `${value}`;
                return label.length > MAX_TICK_LENGTH ? label.slice(0, MAX_TICK_LENGTH) + '...' : label;
            },
        },
        yAxis: { label: this.yAxisLabel(), min: 1, max: this.maxScale() },
        tooltip: {
            label: (item) => `${item.seriesLabel}: ${Math.max(item.value, 0)}%`,
            afterBody: (items) => {
                const exerciseType = (items[0]?.meta as ChartSeriesEntry | undefined)?.['exerciseType'] as string | undefined;
                if (!exerciseType) {
                    return '';
                }
                return (
                    this.translateService.instant('artemisApp.exercise-scores-chart.exerciseType') +
                    ' ' +
                    this.translateService.instant('artemisApp.exercise-scores-chart.' + exerciseType.toLowerCase().replace('-', '_'))
                );
            },
        },
    }));

    constructor() {
        this.translateService.onLangChange.pipe(takeUntilDestroyed()).subscribe(() => {
            this.setTranslations();
        });

        effect(() => {
            this.filteredExerciseIDs(); // Read signal to track it
            untracked(() => {
                this.initializeChart();
            });
        });
    }

    ngAfterViewInit() {
        this.activatedRoute.parent?.parent?.params.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
            this.courseId = +params['courseId'];
            if (this.courseId) {
                this.loadDataAndInitializeChart();
            }
        });
    }

    private loadDataAndInitializeChart(): void {
        this.isLoading = true;
        this.exerciseScoresChartService
            .getExerciseScoresForCourse(this.courseId)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
            )
            .subscribe({
                next: (exerciseScoresResponse) => {
                    this.exerciseScores = exerciseScoresResponse.body!;
                    this.initializeChart();
                },
                error: (errorResponse: HttpErrorResponse) => onError(this.alertService, errorResponse),
            });
    }

    private initializeChart(): void {
        this.setTranslations();
        this.exerciseScores = this.exerciseScores.concat(this.excludedExerciseScores);
        this.excludedExerciseScores = this.exerciseScores.filter((score) => this.filteredExerciseIDs().includes(score.exerciseId!));
        this.exerciseScores = this.exerciseScores.filter((score) => !this.filteredExerciseIDs().includes(score.exerciseId!));
        this.visibleExerciseScores = Array.of(...this.exerciseScores);
        // we show all the exercises ordered by their release data
        const sortedExerciseScores = sortBy(this.exerciseScores, (exerciseScore) => exerciseScore.releaseDate);
        this.exerciseTypeFilter.initializeFilterOptions(sortedExerciseScores);
        this.addData(sortedExerciseScores);
    }

    /**
     * Converts the exerciseScoresDTOs into dedicated objects that can be processed by the chart in order to
     * visualize the scores
     * @param exerciseScoresDTOs array of objects containing the students score, the average score for this exercise and
     * the max score achieved for this exercise by a student as well as other detailed information of the exericse
     */
    private addData(exerciseScoresDTOs: ExerciseScoresDTO[]): void {
        const scoreSeries: ChartMultiSeriesEntry['series'] = [];
        const averageSeries: ChartMultiSeriesEntry['series'] = [];
        const bestScoreSeries: ChartMultiSeriesEntry['series'] = [];
        let maxScale = 101;
        exerciseScoresDTOs.forEach((exerciseScoreDTO) => {
            const exerciseId = exerciseScoreDTO.exerciseId!;
            const exerciseType = exerciseScoreDTO.exerciseType;
            // adapt the y-axis max
            maxScale = Math.max(round(exerciseScoreDTO.scoreOfStudent!), round(exerciseScoreDTO.averageScoreAchieved!), round(exerciseScoreDTO.maxScoreAchieved!), maxScale);
            scoreSeries.push({ name: exerciseScoreDTO.exerciseTitle!, value: round(exerciseScoreDTO.scoreOfStudent!), exerciseId, exerciseType });
            averageSeries.push({ name: exerciseScoreDTO.exerciseTitle!, value: round(exerciseScoreDTO.averageScoreAchieved!), exerciseId, exerciseType });
            bestScoreSeries.push({ name: exerciseScoreDTO.exerciseTitle!, value: round(exerciseScoreDTO.maxScoreAchieved!), exerciseId, exerciseType });
        });

        this.maxScale.set(maxScale);
        this.chartEntries.set([
            { name: this.yourScoreLabel, series: scoreSeries },
            { name: this.averageScoreLabel, series: averageSeries },
            { name: this.maximumScoreLabel, series: bestScoreSeries },
        ]);
    }

    /**
     * Provides the functionality when the user clicks on a data point of the chart:
     * the user gets delegated to the corresponding exercise detail page.
     * Toggling the visibility of a line is handled by the chart legend itself.
     * @param event the selection event emitted by the chart
     */
    onSelect(event: TumUiChartSelectEvent): void {
        const exerciseId = (event.meta as ChartSeriesEntry | undefined)?.['exerciseId'];
        if (exerciseId !== undefined) {
            this.navigateToExercise(exerciseId as number);
        }
    }

    /**
     * We navigate to the exercise sub page in a new tab when the user clicks on a data point
     */
    navigateToExercise(exerciseId: number): void {
        this.navigationUtilService.routeInNewTab(['courses', this.courseId, 'exercises', exerciseId]);
    }

    /**
     * Handles selection or deselection of specific exercise type
     * @param type the ExerciseType the user changed the filter for
     */
    toggleType(type: ExerciseType): void {
        this.visibleExerciseScores = this.exerciseTypeFilter.toggleExerciseType(type, this.exerciseScores);
        // we show all the exercises ordered by their release data
        const sortedExerciseScores = sortBy(this.visibleExerciseScores, (exerciseScore) => exerciseScore.releaseDate);
        this.addData(sortedExerciseScores);
    }

    /**
     * Auxiliary method that instantiated the translations for the exercise.
     * As we subscribe to language changes, this ensures that the chart is translated instantly if the user changes the language
     */
    private setTranslations(): void {
        this.xAxisLabel.set(this.translateService.instant('artemisApp.exercise-scores-chart.xAxis'));
        this.yAxisLabel.set(this.translateService.instant('artemisApp.exercise-scores-chart.yAxis'));

        this.yourScoreLabel = this.translateService.instant('artemisApp.exercise-scores-chart.yourScoreLabel');
        this.averageScoreLabel = this.translateService.instant('artemisApp.exercise-scores-chart.averageScoreLabel');
        this.maximumScoreLabel = this.translateService.instant('artemisApp.exercise-scores-chart.maximumScoreLabel');

        const entries = this.chartEntries();
        if (entries.length > 0) {
            const labels = [this.yourScoreLabel, this.averageScoreLabel, this.maximumScoreLabel];
            this.chartEntries.set(entries.map((entry, index) => ({ name: labels[index], series: entry.series })));
        }
    }
}
