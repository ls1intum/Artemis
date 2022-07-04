import { Component, Input, OnInit } from '@angular/core';
import { GraphColors, SpanType } from 'app/entities/statistics.model';
import { CourseManagementStatisticsModel } from 'app/entities/quiz/course-management-statistics-model';
import { faArrowLeft, faArrowRight, faFilter } from '@fortawesome/free-solid-svg-icons';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { ExerciseType } from 'app/entities/exercise.model';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { axisTickFormattingWithPercentageSign } from 'app/shared/statistics-graph/statistics-graph.utils';
import { ChartExerciseTypeFilterDirective } from 'app/shared/chart/chart-exercise-type-filter.directive';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { ThemeService } from 'app/core/theme/theme.service';

interface ExerciseStatisticsEntry extends NgxChartsSingleSeriesDataEntry {
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
    styleUrls: ['./statistics-average-score-graph.component.scss', '../chart/vertical-bar-chart.scss'],
})
export class StatisticsAverageScoreGraphComponent extends ChartExerciseTypeFilterDirective implements OnInit {
    @Input()
    exerciseAverageScores: CourseManagementStatisticsModel[];
    @Input()
    courseAverage: number;
    @Input()
    courseId: number;

    // Html properties
    LEFT = false;
    RIGHT = true;
    SpanType = SpanType;

    // Data
    barChartLabels: string[] = [];
    // ngx
    ngxData: ExerciseStatisticsEntry[] = [];
    ngxColor = {
        name: 'Course statistics',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    } as Color;

    // for filtering
    exerciseScoresFilteredByPerformanceInterval: CourseManagementStatisticsModel[];
    currentlyDisplayableExercises: CourseManagementStatisticsModel[];
    displayColorMap = new Map<PerformanceInterval, string>();
    numberOfSelectedIntervals = 3;

    readonly yAxisTickFormatting = axisTickFormattingWithPercentageSign;
    readonly performanceIntervals = [PerformanceInterval.LOWEST, PerformanceInterval.AVERAGE, PerformanceInterval.BEST];
    readonly convertToMapKey = ChartExerciseTypeFilterDirective.convertToMapKey;
    readonly CRITICAL_CLASS = 'critical-color';
    readonly MEDIAN_CLASS = 'median-color';
    readonly BEST_CLASS = 'best-color';
    readonly maxSpanSize = 10; // The maximum amount of exercises displayable in one scope

    weakestThirdUpperBoundary: number;
    bestThirdLowerBoundary: number;

    // Left arrow -> decrease, right arrow -> increase
    currentPeriod = 0;
    currentSize = 0;

    // Icons
    faArrowLeft = faArrowLeft;
    faArrowRight = faArrowRight;
    faFilter = faFilter;

    constructor(private themeService: ThemeService, private navigationUtilService: ArtemisNavigationUtilService) {
        super();
    }

    ngOnInit(): void {
        this.initializeChart();
    }

    private initializeChart(): void {
        this.includeAllIntervals();
        this.exerciseAverageScores = this.orderAverageScores(this.exerciseAverageScores);
        this.setUpColorDistribution();
        this.initializeFilterOptions(this.exerciseAverageScores);
        this.setupChart(this.exerciseAverageScores);
        this.currentlyDisplayableExercises = this.exerciseAverageScores;
        this.exerciseScoresFilteredByPerformanceInterval = this.exerciseAverageScores;
        this.currentSize = this.exerciseAverageScores.length;
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
     * @private
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
     * @param event the event that is passed by the framework
     */
    onSelect(event: any): void {
        const dataEntry = this.determineChartEntry(event.name, event.value);

        // a workaround in order to prevent false navigation. If more than one exercise is matching, no routing is done
        if (dataEntry) {
            const route = ['course-management', this.courseId, '', dataEntry.exerciseId, 'exercise-statistics'];
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
     * @private
     */
    private determineChartEntry(name: string, value: number): ExerciseStatisticsEntry | undefined {
        let counter = 0;
        let result;
        /*
         * The emitted event only contains the name and the average score of the exercise. Using those values to determine the chart entry
         * is not an ideal solution as this pair is not necessarily unique.
         * In practice they most likely are unique, though. Not being able to find the entry in this edge case therefore has negligible impact.
         */
        this.ngxData.forEach((exercise) => {
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
     * Sets up chart labels, the dedicated objects in order to represent the exercises by ngx-charts and the bar coloring
     * @param exerciseModels the models representing the course exercises
     * @private
     */
    private setupChart(exerciseModels: CourseManagementStatisticsModel[]): void {
        this.barChartLabels = exerciseModels.slice(this.currentPeriod, 10 + this.currentPeriod).map((exercise) => exercise.exerciseName);
        this.ngxData = exerciseModels.slice(this.currentPeriod, 10 + this.currentPeriod).map(
            (exercise, index) =>
                ({
                    name: this.barChartLabels[index],
                    value: exercise.averageScore,
                    exerciseType: exercise.exerciseType,
                    exerciseId: exercise.exerciseId,
                } as ExerciseStatisticsEntry),
        );
        this.ngxColor.domain = this.ngxData.map((exercise) => this.determineColor(exercise.value));
    }

    /**
     * Sets up the color distribution for the chart that is later on used to determine the color for every bar
     * The 33% lowest performing exercises are colored red.
     * The 33% average performing exercises are colored grey.
     * The 33% best performing exercises are colored green.
     * This method only identifies the threshold scores for the lowest and highest performing exercises.
     * These are exclusive, which means that both boundary values are excluded by the lowest and best third accordingly
     * @private
     */
    private setUpColorDistribution(): void {
        if (!this.exerciseAverageScores || this.exerciseAverageScores.length === 0) {
            return;
        }
        const averageScores = this.exerciseAverageScores.map((exercise) => exercise.averageScore);
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
        this.currentlyDisplayableExercises = this.orderAverageScores(this.toggleExerciseType(type, this.exerciseScoresFilteredByPerformanceInterval));
        this.currentPeriod = 0;
        this.setupChart(this.currentlyDisplayableExercises);
        this.currentSize = this.currentlyDisplayableExercises.length;
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
                this.exerciseScoresFilteredByPerformanceInterval = this.orderAverageScores(this.exerciseAverageScores);
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
     * @private
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
        return this.exerciseAverageScores.filter(filterFunction);
    }

    /**
     * Auxiliary method reducing code duplication for sorting the model array ascending in its averageScores
     * @param exerciseModels the array that should be ordered
     * @private
     */
    private orderAverageScores(exerciseModels: CourseManagementStatisticsModel[]): CourseManagementStatisticsModel[] {
        return exerciseModels.sort((exercise1, exercise2) => exercise1.averageScore - exercise2.averageScore);
    }

    /**
     * Auxiliary method reducing code duplication for initializing the chart after a performance interval has been selected
     * @private
     */
    private initializeFilterOptionsAndSetupChartWithCurrentVisibleScores(): void {
        this.initializeFilterOptions(this.exerciseScoresFilteredByPerformanceInterval);
        this.setupChart(this.exerciseScoresFilteredByPerformanceInterval);
        this.currentlyDisplayableExercises = this.exerciseScoresFilteredByPerformanceInterval;
        this.currentSize = this.exerciseScoresFilteredByPerformanceInterval.length;
    }

    /**
     * Sets all performance intervals to visible
     * @private
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
     * @private
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
     * Appends a percentage sign to every data label of the chart
     * @param averageScore the score that is displayed by the data label
     */
    formatDataLabel(averageScore: number): string {
        return averageScore + '%';
    }
}
