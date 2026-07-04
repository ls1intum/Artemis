import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { AggregatedExerciseGroupResult } from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { LocaleConversionService } from 'app/foundation/service/locale-conversion.service';
import { roundValueSpecifiedByCourseSettings } from 'app/foundation/util/utils';
import { ActivatedRoute } from '@angular/router';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ArtemisNavigationUtilService, navigateToExamExercise } from 'app/foundation/util/navigation.utils';
import { Course } from 'app/course/shared/entities/course.model';
import { TranslateService } from '@ngx-translate/core';
import { ChartModule } from 'primeng/chart';
import { ChartSeriesEntry } from 'app/shared-ui/chart/chart-data.model';
import { ChartColorService } from 'app/shared-ui/chart/chart-color.service';
import { singleSeriesChartData } from 'app/shared-ui/chart/chart-adapters';
import { barChartOptions, toChartSelectEvent } from 'app/shared-ui/chart/chart-options';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

/** Per-bar metadata used for tooltips and click navigation, keyed by the bar's chart name. */
interface ExamScoreLookupEntry {
    absoluteValue?: number;
    exerciseId?: number;
    exerciseType?: ExerciseType;
}

type NameToValueMap = { [name: string]: ExamScoreLookupEntry };

@Component({
    selector: 'jhi-exam-scores-average-scores-graph',
    templateUrl: './exam-scores-average-scores-graph.component.html',
    imports: [TranslateDirective, ChartModule],
})
export class ExamScoresAverageScoresGraphComponent implements OnInit {
    private navigationUtilService = inject(ArtemisNavigationUtilService);
    private activatedRoute = inject(ActivatedRoute);
    private localeConversionService = inject(LocaleConversionService);
    private translateService = inject(TranslateService);
    private chartColorService = inject(ChartColorService);

    averageScores = input.required<AggregatedExerciseGroupResult>();
    course = input.required<Course>();

    courseId: number;
    examId: number;

    /** One entry per bar: the exercise group average followed by the exercise averages. */
    readonly chartEntries = signal<ChartSeriesEntry[]>([]);
    /** The raw per-bar colors (CSS variable references), index-aligned with {@link chartEntries}. */
    readonly barColors = signal<string[]>([]);
    readonly xScaleMax = signal(100);
    lookup: NameToValueMap = {};

    private readonly resolvedColors = this.chartColorService.resolvedColors(() => this.barColors());

    readonly chartData = computed(() => singleSeriesChartData(this.chartEntries(), this.resolvedColors()));
    readonly chartOptions = computed(() =>
        barChartOptions({
            horizontal: true,
            percentScale: true,
            xAxis: { max: this.xScaleMax() },
            tooltip: {
                title: (items) => items[0]?.label ?? '',
                label: (item) => {
                    const name = item.label ?? '';
                    const averagePointsTooltip = this.translateService.instant('artemisApp.examScores.averagePointsTooltip');
                    return `${averagePointsTooltip}: ${this.lookupAbsoluteValue(name)} (${this.roundAndPerformLocalConversion(item.parsed.x ?? 0)}%)`;
                },
            },
        }),
    );

    ngOnInit(): void {
        this.activatedRoute.params.subscribe((params) => {
            this.courseId = +params['courseId'];
            this.examId = +params['examId'];
        });
        this.initializeChart();
    }

    private initializeChart(): void {
        const entries: ChartSeriesEntry[] = [];
        const colors: string[] = [];
        let xScaleMax = 100;
        this.lookup[this.averageScores().title] = { absoluteValue: this.averageScores().averagePoints! };
        const exerciseGroupAverage = this.averageScores().averagePercentage ?? 0;
        entries.push({ name: this.averageScores().title, value: exerciseGroupAverage });
        colors.push(this.determineColor(true, exerciseGroupAverage));
        xScaleMax = Math.max(xScaleMax, exerciseGroupAverage);
        this.averageScores().exerciseResults.forEach((exercise) => {
            const exerciseAverage = exercise.averagePercentage ?? 0;
            xScaleMax = Math.max(xScaleMax, exerciseAverage);
            entries.push({ name: exercise.exerciseId + ' ' + exercise.title, value: exerciseAverage });
            this.lookup[exercise.exerciseId + ' ' + exercise.title] = {
                absoluteValue: exercise.averagePoints ?? 0,
                exerciseId: exercise.exerciseId,
                exerciseType: exercise.exerciseType,
            };
            colors.push(this.determineColor(false, exerciseAverage));
        });

        this.chartEntries.set(entries);
        this.barColors.set(colors);
        this.xScaleMax.set(xScaleMax);
    }

    roundAndPerformLocalConversion(points: number | undefined) {
        return this.localeConversionService.toLocaleString(roundValueSpecifiedByCourseSettings(points, this.course()), this.course().accuracyOfScores);
    }

    /**
     * We navigate to the exercise scores page when the user clicks on a data point
     */
    navigateToExercise(exerciseId: number, exerciseType: ExerciseType) {
        navigateToExamExercise(this.navigationUtilService, this.courseId, this.examId, this.averageScores().exerciseGroupId, exerciseType, exerciseId, 'scores');
    }

    /**
     * Looks up the absolute average points of an exercise group or an exercise
     * @param name name of the exercise or exercise type
     * @returns locale string representation of the points
     */
    lookupAbsoluteValue(name: string): string {
        return this.roundAndPerformLocalConversion(this.lookup[name].absoluteValue);
    }

    /**
     * Delegates the user to the scores page of the specific exam exercise if the corresponding bar is clicked
     * @param event the event that is fired by p-chart
     */
    onSelect(event: { element?: { datasetIndex: number; index: number } }) {
        const selected = toChartSelectEvent(event, this.chartData());
        if (!selected?.label) {
            return;
        }
        const id = this.lookup[selected.label]?.exerciseId;
        const type = this.lookup[selected.label]?.exerciseType;
        if (id && type) {
            this.navigateToExercise(id, type);
        }
    }

    /**
     * Determines the color for a given bar
     * @param isExerciseGroup boolean that indicates if the currently determined color is assigned to a bar representing the exercise group average
     * This is necessary because we have a color difference between the exercise group average representation and an exercise average representation
     * @param score the score the bar will represent
     */
    private determineColor(isExerciseGroup: boolean, score: number): string {
        if (score >= 50) {
            return isExerciseGroup ? GraphColors.BLUE : GraphColors.DARK_BLUE;
        } else if (score > 25) {
            return GraphColors.YELLOW;
        } else {
            return GraphColors.RED;
        }
    }
}
