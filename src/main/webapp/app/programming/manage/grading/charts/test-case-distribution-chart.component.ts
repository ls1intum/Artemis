import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { ProgrammingExerciseTestCase, Visibility } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { TestCaseStatsMap } from 'app/programming/shared/entities/programming-exercise-test-case-statistics.model';
import { TranslateService } from '@ngx-translate/core';
import { getColor } from 'app/programming/manage/grading/charts/programming-grading-charts.utils';
import { ProgrammingGradingChartsDirective } from 'app/programming/manage/grading/charts/programming-grading-charts.directive';
import { getTotalMaxPoints } from 'app/exercise/util/exercise.utils';
import { ChartMultiSeriesEntry } from 'app/shared-ui/chart/chart-data.model';
import { ChartColorService } from 'app/shared-ui/chart/chart-color.service';
import { multiSeriesToNormalizedStackedBarData, multiSeriesToStackedBarData } from 'app/shared-ui/chart/chart-adapters';
import { barChartOptions, toChartSelectEvent } from 'app/shared-ui/chart/chart-options';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ChartModule } from 'primeng/chart';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

type TestCaseColors = {
    [label: string]: string;
};

@Component({
    selector: 'jhi-test-case-distribution-chart',
    styleUrls: ['./sca-category-distribution-chart.scss'],
    templateUrl: './test-case-distribution-chart.component.html',
    imports: [TranslateDirective, ChartModule, ArtemisTranslatePipe],
})
export class TestCaseDistributionChartComponent extends ProgrammingGradingChartsDirective {
    private translateService = inject(TranslateService);
    private navigationUtilService = inject(ArtemisNavigationUtilService);

    readonly testCases = input<ProgrammingExerciseTestCase[]>([]);
    readonly testCaseStatsMap = input<TestCaseStatsMap>();
    readonly totalParticipations = input<number>();
    readonly exercise = input.required<ProgrammingExercise>();

    readonly testCaseColorsChange = output<any>();
    readonly testCaseRowFilter = output<number>();

    // visible test cases (filtered out the ones that are never visible), exposed for templates and tests
    readonly processedTestCases = computed<ProgrammingExerciseTestCase[]>(() => (this.testCases() ?? []).filter((testCase) => testCase.visibility !== Visibility.Never));

    totalWeight: number;

    // array containing the entries in order to display the weight and bonus chart (one entry per bar)
    private weightEntries: ChartMultiSeriesEntry[] = [
        { name: '', series: [] },
        { name: '', series: [] },
    ];
    // array containing the entries in order to display the points chart
    private pointsEntries: ChartMultiSeriesEntry[] = [{ name: '', series: [] }];

    readonly weightData = signal<ChartMultiSeriesEntry[]>([]);
    readonly pointsData = signal<ChartMultiSeriesEntry[]>([]);

    private readonly resolvedColors = inject(ChartColorService).resolvedColors(() => this.chartColors());

    readonly weightChartData = computed(() => multiSeriesToNormalizedStackedBarData(this.weightData(), this.resolvedColors()));
    readonly pointsChartData = computed(() => multiSeriesToStackedBarData(this.pointsData(), this.resolvedColors()));

    readonly weightChartOptions = computed(() =>
        barChartOptions({
            horizontal: true,
            stacked: true,
            percentScale: true,
            xAxis: { tickFormatter: this.xAxisFormatting },
            tooltip: {
                title: (items) => items[0]?.dataset.label ?? '',
                label: (item) => {
                    const meta = item.dataset.meta?.[item.dataIndex];
                    if (!meta) {
                        return '';
                    }
                    // bar 0 carries the weight as value and the bonus as extra, bar 1 vice versa
                    const isWeightBar = item.dataIndex === 0;
                    const weightPercentage = ((isWeightBar ? meta.value : (meta.weight as number)) ?? 0).toFixed(2);
                    const bonusPercentage = ((isWeightBar ? (meta.bonus as number) : meta.value) ?? 0).toFixed(2);
                    return [
                        this.translateService.instant('artemisApp.programmingExercise.configureGrading.charts.testCaseWeights.weightTooltip', { percentage: weightPercentage }),
                        this.translateService.instant('artemisApp.programmingExercise.configureGrading.charts.testCaseWeights.weightAndBonusTooltip', {
                            percentage: bonusPercentage,
                        }),
                    ];
                },
            },
        }),
    );
    readonly pointsChartOptions = computed(() =>
        barChartOptions({
            horizontal: true,
            stacked: true,
            xAxis: { max: 100, tickFormatter: this.xAxisFormatting },
            tooltip: {
                title: (items) => items[0]?.dataset.label ?? '',
                label: (item) => {
                    const value = ((item.dataset.meta?.[item.dataIndex]?.value as number) ?? 0).toFixed(2);
                    return this.translateService.instant('artemisApp.programmingExercise.configureGrading.charts.testCasePoints.pointsTooltip', { percentage: value });
                },
            },
        }),
    );

    constructor() {
        super();

        this.translateService.onLangChange.subscribe(() => {
            this.updateTranslation();
        });

        this.updateTranslation();

        effect(() => {
            this.computeChartData();
        });
    }

    private computeChartData(): void {
        const testCases = this.processedTestCases();
        const testCaseStatsMap = this.testCaseStatsMap();
        const totalParticipations = this.totalParticipations();
        const exercise = this.exercise();

        // sum of all weights
        this.totalWeight = testCases.reduce((sum, testCase) => sum + testCase.weight!, 0);
        // max points for the exercise
        const maxPoints = exercise.maxPoints!;
        // exercise max score with bonus in percent
        const maxScoreInPercent = getTotalMaxPoints(exercise) / maxPoints;

        // total of achievable points for this exercise
        const totalPoints = maxPoints * (totalParticipations || 0);

        const testCaseScores = testCases.map((testCase) => {
            // calculated score for this test case
            const testCaseScore = (this.totalWeight > 0 ? (testCase.weight! * testCase.bonusMultiplier!) / this.totalWeight : 0) + (testCase.bonusPoints || 0) / maxPoints;

            const score = Math.min(testCaseScore, maxScoreInPercent);
            const stats = testCaseStatsMap ? testCaseStatsMap[testCase.testName!] : undefined;

            return {
                id: testCase.id,
                label: testCase.testName!,
                // relative weight percentage
                relWeight: this.totalWeight > 0 ? (testCase.weight! / this.totalWeight) * 100 : 0,
                // relative score percentage
                relScore: score * 100,
                // relative points percentage
                relPoints: stats && totalPoints > 0 ? ((stats.numPassed! * score * maxPoints) / totalPoints) * 100 : 0,
            };
        });

        if (this.weightEntries[0].series.length !== testCaseScores.length) {
            const testCaseColors: TestCaseColors = {};

            this.weightEntries = [];
            this.pointsEntries = [];
            const colors: string[] = [];

            const weight: ChartMultiSeriesEntry = {
                name: this.translateService.instant('artemisApp.programmingExercise.configureGrading.charts.testCaseWeights.weight'),
                series: [],
            };
            const weightAndBonus: ChartMultiSeriesEntry = {
                name: this.translateService.instant('artemisApp.programmingExercise.configureGrading.charts.testCaseWeights.weightAndBonus'),
                series: [],
            };

            const points: ChartMultiSeriesEntry = {
                name: this.translateService.instant('artemisApp.programmingExercise.configureGrading.charts.testCasePoints.points'),
                series: [],
            };

            for (let i = 0; i < testCaseScores.length; i++) {
                const element = testCaseScores[i];

                const label = element.label;
                const color = getColor(i / testCases.length, 50);

                weight.series.push({ name: label, value: Math.max(element.relWeight, 0), bonus: Math.max(element.relScore, 0), id: element.id });
                weightAndBonus.series.push({ name: label, value: Math.max(element.relScore, 0), weight: Math.max(element.relScore, 0), id: element.id });

                points.series.push({ name: label, value: Math.max(element.relPoints, 0) });

                testCaseColors[label] = color;
                colors.push(color);
            }

            this.weightEntries.push(weight);
            this.weightEntries.push(weightAndBonus);

            this.pointsEntries.push(points);
            this.chartColors.set(colors);

            // update colors for test case table
            this.testCaseColorsChange.emit(testCaseColors);
        } else {
            // update values in-place
            testCaseScores.forEach((score) => {
                this.weightEntries[0].series.forEach((weight, index) => {
                    if (weight.id === score.id) {
                        weight.value = Math.max(score.relWeight, 0);
                        weight.bonus = Math.max(score.relScore, 0);
                        // the bars are set up symmetrically, which means if we have the index of the corresponding test case in one bar, it is the same for all other bars
                        this.weightEntries[1].series[index].value = Math.max(score.relScore, 0);
                        this.weightEntries[1].series[index].weight = Math.max(score.relWeight, 0);
                        this.pointsEntries[0].series[index].value = Math.max(score.relPoints, 0);
                    }
                });
            });
        }

        this.weightData.set([...this.weightEntries]);
        this.pointsData.set([...this.pointsEntries]);
    }

    /**
     * Auxiliary method that handles the click on the points chart
     * Delegates the user to the statistics page of the programming exercise
     */
    onSelectPoints(): void {
        const exercise = this.exercise();
        this.navigationUtilService.routeInNewTab(['course-management', exercise.course!.id, 'programming-exercises', exercise.id, 'exercise-statistics']);
    }

    /**
     * Auxiliary method that handles the click on the weight and bonus chart
     * Filters the table left to the charts in order to display only the test case that is clicked
     * @param event event that is delegated by p-chart and identifies the clicked segment
     */
    onSelectWeight(event: any): void {
        const selected = toChartSelectEvent(event, this.weightChartData());
        const testCaseId = selected?.meta?.['id'];
        if (testCaseId === undefined) {
            return;
        }
        this.tableFiltered = true;
        this.testCaseRowFilter.emit(testCaseId as number);
    }

    /**
     * Auxiliary method that
     */
    resetTableFilter(): void {
        this.tableFiltered = false;
        this.testCaseRowFilter.emit(ProgrammingGradingChartsDirective.RESET_TABLE);
    }

    /**
     * Auxiliary method in order to keep the translation of the bar labels up to date
     */
    updateTranslation(): void {
        const weightLabel = this.translateService.instant('artemisApp.programmingExercise.configureGrading.charts.testCaseWeights.weight');
        const weightAndBonusLabel = this.translateService.instant('artemisApp.programmingExercise.configureGrading.charts.testCaseWeights.weightAndBonus');
        const pointsLabel = this.translateService.instant('artemisApp.programmingExercise.configureGrading.charts.testCasePoints.points');

        this.weightEntries[0].name = weightLabel;
        this.weightEntries[1].name = weightAndBonusLabel;
        this.pointsEntries[0].name = pointsLabel;

        this.weightData.set([...this.weightEntries]);
        this.pointsData.set([...this.pointsEntries]);
    }
}
