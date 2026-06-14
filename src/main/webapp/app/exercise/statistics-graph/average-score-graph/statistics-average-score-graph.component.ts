import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { GraphColors, SpanType } from 'app/exercise/shared/entities/statistics.model';
import { CourseManagementStatisticsModel } from 'app/quiz/shared/entities/course-management-statistics-model';
import { faArrowLeft, faArrowRight, faFilter } from '@fortawesome/free-solid-svg-icons';
import ChartDataLabels from 'chartjs-plugin-datalabels';
import { TranslateService } from '@ngx-translate/core';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ChartSeriesEntry } from 'app/shared-ui/chart/chart-data.model';
import { ChartColorService } from 'app/shared-ui/chart/chart-color.service';
import { singleSeriesChartData } from 'app/shared-ui/chart/chart-adapters';
import { barChartOptions, toChartSelectEvent } from 'app/shared-ui/chart/chart-options';
import { axisTickFormattingWithPercentageSign } from 'app/exercise/statistics-graph/util/statistics-graph.utils';
import { ChartExerciseTypeFilter } from 'app/exercise/chart/chart-exercise-type-filter';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { ChartCategoryFilter } from 'app/exercise/chart/chart-category-filter';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { NgClass } from '@angular/common';
import { ChartModule } from 'primeng/chart';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

interface ExerciseStatisticsEntry extends ChartSeriesEntry {
    exerciseType: ExerciseType;
    exerciseId: number;
}

export enum PerformanceInterval {
    LOWEST = 'lowest',
    AVERAGE = 'average',
    BEST = 'best',
}

@Component({
    selector: 'jhi-statistics-average-score-graph',
    templateUrl: './statistics-average-score-graph.component.html',
    styleUrls: ['./statistics-average-score-graph.component.scss'],
    imports: [TranslateDirective, FaIconComponent, ChartModule, NgbDropdown, NgbDropdownToggle, NgbDropdownMenu, NgClass, ArtemisTranslatePipe],
})
export class StatisticsAverageScoreGraphComponent implements OnInit {
    private navigationUtilService = inject(ArtemisNavigationUtilService);
    private translateService = inject(TranslateService);
    readonly exerciseTypeFilter = inject(ChartExerciseTypeFilter);
    readonly chartCategoryFilter = inject(ChartCategoryFilter);

    readonly exerciseAverageScores = input.required<CourseManagementStatisticsModel[]>();
    readonly courseAverage = input.required<number>();
    readonly courseId = input.required<number>();

    /** the input scores ordered ascending by average score; the working set the chart and filters operate on */
    private orderedScores: CourseManagementStatisticsModel[] = [];

    // Html properties
    LEFT = false;
    RIGHT = true;
    SpanType = SpanType;

    // Data
    barChartLabels: string[] = [];
    readonly chartEntries = signal<ExerciseStatisticsEntry[]>([]);
    /** the raw per-bar colors (GraphColors values); resolved to concrete theme colors below */
    readonly barColors = signal<string[]>([]);

    private readonly resolvedColors = inject(ChartColorService).resolvedColors(() => this.barColors());

    readonly chartData = computed(() => singleSeriesChartData(this.chartEntries(), this.resolvedColors()));
    readonly chartOptions = computed(() =>
        barChartOptions({
            yAxis: { max: 100, tickFormatter: (value) => axisTickFormattingWithPercentageSign(String(value)) },
            tooltip: {
                label: (item) => {
                    const name = String(item.label ?? '');
                    const value = item.parsed.y ?? 0;
                    return [
                        `${this.translateService.instant('artemisApp.courseStatistics.exerciseAverage')}: ${value}%`,
                        `${this.translateService.instant('artemisApp.courseStatistics.exerciseType')}: ${this.translateService.instant(
                            'artemisApp.courseStatistics.' + this.convertTypeForTooltip(name, value),
                        )}`,
                    ];
                },
            },
            dataLabels: { formatter: (value) => this.formatDataLabel(value) },
        }),
    );
    /** chartjs-plugin-datalabels renders the persistent per-bar value labels; pass to <p-chart [plugins]>. */
    readonly dataLabelsPlugin = [ChartDataLabels];

    // for filtering
    exerciseScoresFilteredByPerformanceInterval: CourseManagementStatisticsModel[];
    currentlyDisplayableExercises: CourseManagementStatisticsModel[];
    displayColorMap = new Map<PerformanceInterval, string>();
    numberOfSelectedIntervals = 3;

    readonly performanceIntervals = [PerformanceInterval.LOWEST, PerformanceInterval.AVERAGE, PerformanceInterval.BEST];
    readonly convertToMapKey = ChartExerciseTypeFilter.convertToMapKey;
    readonly CRITICAL_CLASS = 'critical-color';
    readonly MEDIAN_CLASS = 'median-color';
    readonly BEST_CLASS = 'best-color';
    readonly MAX_SPAN_SIZE = 10; // The maximum amount of exercises displayable in one scope

    weakestThirdUpperBoundary: number;
    bestThirdLowerBoundary: number;

    // Left arrow -> decrease, right arrow -> increase
    currentPeriod = 0;
    currentSize = 0;

    // Icons
    faArrowLeft = faArrowLeft;
    faArrowRight = faArrowRight;
    faFilter = faFilter;

    ngOnInit(): void {
        this.initializeChart();
    }

    private initializeChart(): void {
        this.includeAllIntervals();
        this.orderedScores = this.orderAverageScores(this.exerciseAverageScores());
        this.setUpColorDistribution();
        this.exerciseTypeFilter.initializeFilterOptions(this.orderedScores);
        this.chartCategoryFilter.setupCategoryFilter(this.orderedScores);
        this.setupChart(this.orderedScores);
        this.currentlyDisplayableExercises = this.orderedScores;
        this.exerciseScoresFilteredByPerformanceInterval = this.orderedScores;
        this.currentSize = this.orderedScores.length;
    }

    // handles arrow clicks and updates the exercises which are shown, forward is boolean since it is either forward or backward
    public switchTimeSpan(forward: boolean): void {
        this.currentPeriod += forward ? 1 : -1;
        this.setupChart(this.currentlyDisplayableExercises);
    }

    /**
     * Determines the color of the bar given the score
     * @param score that is represented by the bar
     * @returns string rgba representation of the color
     */
    private determineColor(score: number): string {
        if (score > this.bestThirdLowerBoundary) {
            return GraphColors.GREEN;
        } else if (score < this.weakestThirdUpperBoundary) {
            return GraphColors.RED;
        }
        return GraphColors.GREY;
    }

    /**
     * Handles the click event on one of the bars and navigates to the corresponding exercise statistics page
     * @param event the event that is passed by p-chart
     */
    onSelect(event: any): void {
        const selected = toChartSelectEvent(event, this.chartData());
        const dataEntry = selected?.meta as ExerciseStatisticsEntry | undefined;

        if (dataEntry) {
            const route = ['course-management', this.courseId(), '', dataEntry.exerciseId, 'exercise-statistics'];
            let type = dataEntry.exerciseType.toLowerCase();
            if (type === ExerciseType.QUIZ) {
                route[4] = 'quiz-point-statistic';
            } else if (type === 'file_upload') {
                type = 'file-upload';
            }
            route[2] = type + '-exercises';
            this.navigationUtilService.routeInNewTab(route);
        }
    }

    /**
     * Determines the entry in the chart for a mouse event
     * @param name name of the exercise
     * @param value average score of the exercise
     */
    private determineChartEntry(name: string, value: number): ExerciseStatisticsEntry | undefined {
        let counter = 0;
        let result;
        /*
         * The emitted event only contains the name and the average score of the exercise. Using those values to determine the chart entry
         * is not an ideal solution as this pair is not necessarily unique.
         * In practice they most likely are unique, though. Not being able to find the entry in this edge case therefore has negligible impact.
         */
        this.chartEntries().forEach((exercise) => {
            if (exercise.name === name && exercise.value === value) {
                counter++;
                result = exercise;
            }
        });
        // if more than one exercise match, we do not navigate
        return counter === 1 ? result : undefined;
    }

    /**
     * Converts the exercise type contained in the ExerciseStatisticEntries for the tooltip in order to make it translatable
     * @param name the name of the exercise
     * @param value the average score of the exercise
     */
    convertTypeForTooltip(name: string, value: number): string {
        const entry = this.determineChartEntry(name, value);
        if (entry) {
            const type = entry.exerciseType.toLowerCase();
            if (type === 'file_upload') {
                return 'file-upload';
            }
            return type;
        } else {
            // if the name and value is not unique, we cannot determine the type
            return '';
        }
    }

    /**
     * Sets up chart labels, the dedicated objects in order to represent the exercises in the chart and the bar coloring
     * @param exerciseModels the models representing the course exercises
     */
    private setupChart(exerciseModels: CourseManagementStatisticsModel[]): void {
        this.barChartLabels = exerciseModels.slice(this.currentPeriod, 10 + this.currentPeriod).map((exercise) => exercise.exerciseName);
        this.chartEntries.set(
            exerciseModels.slice(this.currentPeriod, 10 + this.currentPeriod).map(
                (exercise, index) =>
                    ({
                        name: this.barChartLabels[index],
                        value: exercise.averageScore,
                        exerciseType: exercise.exerciseType,
                        exerciseId: exercise.exerciseId,
                    }) as ExerciseStatisticsEntry,
            ),
        );
        this.barColors.set(this.chartEntries().map((exercise) => this.determineColor(exercise.value)));
    }

    /**
     * Sets up the color distribution for the chart that is later on used to determine the color for every bar
     * The 33% lowest performing exercises are colored red.
     * The 33% average performing exercises are colored grey.
     * The 33% best performing exercises are colored green.
     * This method only identifies the threshold scores for the lowest and highest performing exercises.
     * These are exclusive, which means that both boundary values are excluded by the lowest and best third accordingly
     */
    private setUpColorDistribution(): void {
        if (this.orderedScores.length === 0) {
            return;
        }
        const averageScores = this.orderedScores.map((exercise) => exercise.averageScore);
        const thirdSize = Math.floor(averageScores.length / 3);
        const highestScoreInLowestThird = averageScores[Math.max(thirdSize - 1, 0)];
        const allScoresAboveLowestThird = averageScores.filter((score) => score > highestScoreInLowestThird);
        this.weakestThirdUpperBoundary = allScoresAboveLowestThird.length > 0 ? Math.min(...allScoresAboveLowestThird) : highestScoreInLowestThird;
        const lowestScoreInBestThird = averageScores[Math.min(averageScores.length - thirdSize, averageScores.length - 1)];
        const allScoresBelowBestThird = averageScores.filter((score) => score < lowestScoreInBestThird);
        this.bestThirdLowerBoundary = allScoresBelowBestThird.length > 0 ? Math.max(...allScoresBelowBestThird) : lowestScoreInBestThird;
    }

    /**
     * Wrapper method that handles the filtering per exercise type and sets up the chart accordingly
     * @param type the exercise type that is filtered against
     */
    toggleType(type: ExerciseType): void {
        const filteredAgainstType = this.exerciseTypeFilter.toggleExerciseType<CourseManagementStatisticsModel>(type, this.exerciseScoresFilteredByPerformanceInterval);
        const filteredAgainstCategory = this.chartCategoryFilter.applyCurrentFilter<CourseManagementStatisticsModel>(this.exerciseScoresFilteredByPerformanceInterval);
        this.initializeChartWithFilter(filteredAgainstCategory, filteredAgainstType);
    }

    /**
     * Performs a filtering for a performance interval (legend entry).
     * If all intervals are currently displayed and one is clicked, for this interval is filtered.
     * If a disabled interval is clicked, this interval is added to the filter.
     * @param interval the interval that is selected
     */
    togglePerformanceInterval(interval: PerformanceInterval): void {
        const currentValue = this.displayColorMap.get(interval);
        // we reset the view of the chart (changed with the arrows) before displaying the filtered exercises
        this.currentPeriod = 0;
        let newValue = '';
        // This means an interval is selected that is currently already displayed
        if (currentValue !== '') {
            // if only this interval is selected, reselecting it leads to selecting all intervals
            if (this.numberOfSelectedIntervals === 1) {
                this.includeAllIntervals();
                this.exerciseScoresFilteredByPerformanceInterval = this.orderAverageScores(this.orderedScores);
            } else {
                this.deselectAllOtherIntervals(interval);
            }
            this.initializeFilterOptionsAndSetupChartWithCurrentVisibleScores();
            return;
        }
        switch (interval) {
            case PerformanceInterval.LOWEST: {
                newValue = this.CRITICAL_CLASS;
                break;
            }
            case PerformanceInterval.AVERAGE: {
                newValue = this.MEDIAN_CLASS;
                break;
            }
            case PerformanceInterval.BEST: {
                newValue = this.BEST_CLASS;
                break;
            }
        }
        // This map determines whether the color legend next to a chart entry is colored or not representing whether this entry is currently visible in the chart or not
        this.displayColorMap.set(interval, newValue);
        this.numberOfSelectedIntervals += 1;
        const exercises = this.filterForPerformanceInterval(interval);
        this.exerciseScoresFilteredByPerformanceInterval = this.orderAverageScores(this.exerciseScoresFilteredByPerformanceInterval.concat(exercises));
        this.initializeFilterOptionsAndSetupChartWithCurrentVisibleScores();
    }

    /**
     * Auxiliary method that identifies the exercises contained by the passed interval
     * @param interval the interval the exercises should be filtered against
     */
    private filterForPerformanceInterval(interval: PerformanceInterval) {
        let filterFunction;
        switch (interval) {
            case PerformanceInterval.LOWEST: {
                filterFunction = (model: CourseManagementStatisticsModel) => model.averageScore < this.weakestThirdUpperBoundary;
                break;
            }
            case PerformanceInterval.AVERAGE: {
                filterFunction = (model: CourseManagementStatisticsModel) =>
                    model.averageScore >= this.weakestThirdUpperBoundary && model.averageScore <= this.bestThirdLowerBoundary;
                break;
            }
            case PerformanceInterval.BEST: {
                filterFunction = (model: CourseManagementStatisticsModel) => model.averageScore > this.bestThirdLowerBoundary;
            }
        }
        return this.orderedScores.filter(filterFunction);
    }

    /**
     * Auxiliary method reducing code duplication for sorting the model array ascending in its averageScores
     * @param exerciseModels the array that should be ordered
     */
    private orderAverageScores(exerciseModels: CourseManagementStatisticsModel[]): CourseManagementStatisticsModel[] {
        return [...exerciseModels].sort((exercise1, exercise2) => exercise1.averageScore - exercise2.averageScore);
    }

    /**
     * Auxiliary method reducing code duplication for initializing the chart after a performance interval has been selected
     */
    private initializeFilterOptionsAndSetupChartWithCurrentVisibleScores(): void {
        this.exerciseTypeFilter.initializeFilterOptions(this.exerciseScoresFilteredByPerformanceInterval);
        this.chartCategoryFilter.setupCategoryFilter(this.exerciseScoresFilteredByPerformanceInterval);
        this.setupChart(this.exerciseScoresFilteredByPerformanceInterval);
        this.currentlyDisplayableExercises = this.exerciseScoresFilteredByPerformanceInterval;
        this.currentSize = this.exerciseScoresFilteredByPerformanceInterval.length;
    }

    /**
     * Sets all performance intervals to visible
     */
    private includeAllIntervals(): void {
        this.displayColorMap.set(PerformanceInterval.LOWEST, this.CRITICAL_CLASS);
        this.displayColorMap.set(PerformanceInterval.AVERAGE, this.MEDIAN_CLASS);
        this.displayColorMap.set(PerformanceInterval.BEST, this.BEST_CLASS);
        this.numberOfSelectedIntervals = 3;
    }

    /**
     * Deselects all performance intervals except the passed one
     * @param interval the interval that should not be deselected
     */
    private deselectAllOtherIntervals(interval: PerformanceInterval): void {
        this.performanceIntervals.forEach((pi) => {
            if (pi !== interval) {
                this.displayColorMap.set(pi, '');
            }
        });
        this.numberOfSelectedIntervals = 1;
        this.exerciseScoresFilteredByPerformanceInterval = this.filterForPerformanceInterval(interval);
    }

    /**
     * Wrapper method that handles the toggling of a category and sets up the chart accordingly
     * @param category the category the user selects or deselects
     */
    toggleCategory(category: string): void {
        const filteredAgainstCategory = this.chartCategoryFilter.toggleCategory<CourseManagementStatisticsModel>(this.exerciseScoresFilteredByPerformanceInterval, category);
        const filteredAgainstType = this.exerciseTypeFilter.applyCurrentFilter<CourseManagementStatisticsModel>(this.exerciseScoresFilteredByPerformanceInterval);
        this.initializeChartWithFilter(filteredAgainstCategory, filteredAgainstType);
    }

    /**
     * Wrapper method that handles the toggling of all categories and sets up the chart accordingly
     */
    toggleAllCategories(): void {
        const filteredAgainstCategory = this.chartCategoryFilter.toggleAllCategories<CourseManagementStatisticsModel>(this.exerciseScoresFilteredByPerformanceInterval);
        const filteredAgainstType = this.exerciseTypeFilter.applyCurrentFilter<CourseManagementStatisticsModel>(this.exerciseScoresFilteredByPerformanceInterval);
        this.initializeChartWithFilter(filteredAgainstCategory, filteredAgainstType);
    }

    /**
     * Wrapper method that handles the toggling of exercises with no categories and sets up the chart accordingly
     */
    toggleExercisesWithNoCategory(): void {
        const filteredAgainstCategory = this.chartCategoryFilter.toggleExercisesWithNoCategory<CourseManagementStatisticsModel>(this.exerciseScoresFilteredByPerformanceInterval);
        const filteredAgainstType = this.exerciseTypeFilter.applyCurrentFilter<CourseManagementStatisticsModel>(this.exerciseScoresFilteredByPerformanceInterval);
        this.initializeChartWithFilter(filteredAgainstCategory, filteredAgainstType);
    }

    /**
     * Auxiliary method to reduce code duplication.
     * Sets the currentPeriod to zero, determines the number of displayable bars and updates the chart.
     * @param filteredAgainstCategory the scores filtered against the current category filter setting
     * @param filteredAgainstType the scores filtered against the current type filter setting
     */
    private initializeChartWithFilter(filteredAgainstCategory: CourseManagementStatisticsModel[], filteredAgainstType: CourseManagementStatisticsModel[]): void {
        this.currentlyDisplayableExercises = this.orderAverageScores(filteredAgainstCategory.filter((score) => filteredAgainstType.includes(score)));
        this.currentPeriod = 0;
        this.setupChart(this.currentlyDisplayableExercises);
        this.currentSize = this.currentlyDisplayableExercises.length;
    }

    /**
     * Appends a percentage sign to every data label of the chart
     * @param averageScore the score that is displayed by the data label
     */
    formatDataLabel(averageScore: number): string {
        return averageScore + '%';
    }
}
