import { Component, Input, OnInit } from '@angular/core';
import { GraphColors, Graphs, SpanType } from 'app/entities/statistics.model';
import { CourseManagementStatisticsModel } from 'app/entities/quiz/course-management-statistics-model';
import { faArrowLeft, faArrowRight, faFilter } from '@fortawesome/free-solid-svg-icons';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { Router } from '@angular/router';
import { ExerciseType } from 'app/entities/exercise.model';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { axisTickFormattingWithPercentageSign } from 'app/shared/statistics-graph/statistics-graph.utils';
import { ChartExerciseTypeFilterDirective } from 'app/shared/chart/chart-exercise-type-filter.directive';

interface ExerciseStatisticsEntry extends NgxChartsSingleSeriesDataEntry {
    exerciseType: ExerciseType;
    exerciseId: number;
}

enum PerformanceInterval {
    CRITICAL,
    MEDIAN,
    BEST,
}

@Component({
    selector: 'jhi-statistics-average-score-graph',
    templateUrl: './statistics-average-score-graph.component.html',
    styleUrls: ['./statistics-average-score-graph.component.scss'],
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
    Graphs = Graphs;

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
    currentlyDisplayedExerciseAverageScores: CourseManagementStatisticsModel[];
    displayColorMap = new Map<PerformanceInterval, string>();

    readonly yAxisTickFormatting = axisTickFormattingWithPercentageSign;
    readonly performanceInterval = PerformanceInterval;
    readonly CRITICAL_CLASS = 'critical-color';
    readonly MEDIAN_CLASS = 'median-color';
    readonly BEST_CLASS = 'best-color';

    weakestThirdUpperBoundary: number;
    bestThirdLowerBoundary: number;

    // Left arrow -> decrease, right arrow -> increase
    currentPeriod = 0;

    // Icons
    faArrowLeft = faArrowLeft;
    faArrowRight = faArrowRight;
    faFilter = faFilter;

    constructor(private router: Router) {
        super();
    }

    ngOnInit(): void {
        this.initializeChart();
    }

    private initializeChart(): void {
        this.displayColorMap.set(PerformanceInterval.CRITICAL, this.CRITICAL_CLASS);
        this.displayColorMap.set(PerformanceInterval.MEDIAN, this.MEDIAN_CLASS);
        this.displayColorMap.set(PerformanceInterval.BEST, this.BEST_CLASS);
        this.exerciseAverageScores = this.orderAverageScores(this.exerciseAverageScores);
        this.setUpColorDistribution();
        this.initializeFilterOptions(this.exerciseAverageScores);
        this.setupChart(this.exerciseAverageScores);
        this.currentlyDisplayedExerciseAverageScores = this.exerciseAverageScores;
    }

    // handles arrow clicks and updates the exercises which are shown, forward is boolean since it is either forward or backward
    public switchTimeSpan(forward: boolean): void {
        this.currentPeriod += forward ? 1 : -1;
        this.setupChart(this.currentlyDisplayedExerciseAverageScores);
    }

    /**
     * Determines the color of the bar given the score
     * @param score that is represented by the bar
     * @returns string rgba representation of the color
     * @private
     */
    private determineColor(score: number): string {
        if (score >= this.bestThirdLowerBoundary) {
            return GraphColors.GREEN;
        } else if (score <= this.weakestThirdUpperBoundary) {
            return GraphColors.RED;
        } else {
            return GraphColors.GREY;
        }
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
            this.router.navigate(route);
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
     * These are inclusive, which means that both boundary values are included by the lowest and best third accordingly
     * @private
     */
    private setUpColorDistribution(): void {
        const averageScores = this.exerciseAverageScores.map((exercise) => exercise.averageScore);
        const thirdSize = Math.floor(averageScores.length / 3);
        this.weakestThirdUpperBoundary = averageScores[Math.max(thirdSize - 1, 0)];
        this.bestThirdLowerBoundary = averageScores[Math.min(averageScores.length - thirdSize, averageScores.length - 1)];
    }

    /**
     * Wrapper method that handles the filtering per exercise type and sets up the chart accordingly
     * @param type the exercise type that is filtered against
     */
    toggleType(type: ExerciseType): void {
        this.currentlyDisplayedExerciseAverageScores = this.orderAverageScores(this.toggleExerciseType(type, this.exerciseAverageScores));
        this.setupChart(this.currentlyDisplayedExerciseAverageScores);
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
            const performanceIntervals = [PerformanceInterval.BEST, PerformanceInterval.MEDIAN, PerformanceInterval.CRITICAL];
            performanceIntervals.forEach((pi) => {
                if (pi !== interval) {
                    this.displayColorMap.set(pi, '');
                }
            });
            this.currentlyDisplayedExerciseAverageScores = this.filterForPerformanceInterval(interval);
            this.initializeFilterOptionsAndSetupChartWithCurrentVisibleScores();
            return;
        }
        switch (interval) {
            case PerformanceInterval.CRITICAL: {
                newValue = this.CRITICAL_CLASS;
                break;
            }
            case PerformanceInterval.MEDIAN: {
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
        const exercises = this.filterForPerformanceInterval(interval);
        this.currentlyDisplayedExerciseAverageScores = this.orderAverageScores(this.currentlyDisplayedExerciseAverageScores.concat(exercises));
    }

    /**
     * Auxiliary method that identifies the exercises contained by the passed interval
     * @param interval the interval the exercises should be filtered against
     * @private
     */
    private filterForPerformanceInterval(interval: PerformanceInterval) {
        let filterFunction;
        // we reset the type filter in order to prevent inconsistencies
        this.typeSet.forEach((type) => {
            this.chartFilter.set(type, true);
        });
        switch (interval) {
            case PerformanceInterval.CRITICAL: {
                filterFunction = (model: CourseManagementStatisticsModel) => model.averageScore <= this.weakestThirdUpperBoundary;
                break;
            }
            case PerformanceInterval.MEDIAN: {
                filterFunction = (model: CourseManagementStatisticsModel) =>
                    model.averageScore > this.weakestThirdUpperBoundary && model.averageScore < this.bestThirdLowerBoundary;
                break;
            }
            case PerformanceInterval.BEST: {
                filterFunction = (model: CourseManagementStatisticsModel) => model.averageScore >= this.bestThirdLowerBoundary;
            }
        }
        return this.exerciseAverageScores.filter(filterFunction);
    }

    /**
     * Auxiliary method reducing code duplication for sorting the model array ascending in its averageScores
     * @param exerciseModels the array that should be ordered
     * @private
     */
    private orderAverageScores(exerciseModels: CourseManagementStatisticsModel[]) {
        return exerciseModels.sort((exercise1, exercise2) => exercise1.averageScore - exercise2.averageScore);
    }

    private initializeFilterOptionsAndSetupChartWithCurrentVisibleScores(): void {
        this.initializeFilterOptions(this.currentlyDisplayedExerciseAverageScores);
        this.setupChart(this.currentlyDisplayedExerciseAverageScores);
    }
}
