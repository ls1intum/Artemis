import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { ProgrammingExerciseTestCase, Visibility } from 'app/entities/programming/programming-exercise-test-case.model';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { TestCaseStatsMap } from 'app/entities/programming/programming-exercise-test-case-statistics.model';
import { TranslateService } from '@ngx-translate/core';
import { getColor } from 'app/exercises/programming/manage/grading/charts/programming-grading-charts.utils';
import { ProgrammingGradingChartsDirective } from 'app/exercises/programming/manage/grading/charts/programming-grading-charts.directive';
import { getTotalMaxPoints } from 'app/exercises/shared/exercise/exercise.utils';
import { NgxChartsMultiSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';

enum TestCaseBarTitle {
    WEIGHT_EN = 'Weight',
    WEIGHT_DE = 'Gewichtung',
    WEIGHT_AND_BONUS_EN = 'Weight & Bonus',
    WEIGHT_AND_BONUS_DE = 'Gewichtung & Bonus',
}

type TestCaseColors = {
    [label: string]: string;
};

@Component({
    selector: 'jhi-test-case-distribution-chart',
    styleUrls: ['./sca-category-distribution-chart.scss'],
    templateUrl: './test-case-distribution-chart.component.html',
})
export class TestCaseDistributionChartComponent extends ProgrammingGradingChartsDirective implements OnInit, OnChanges {
    @Input() testCases: ProgrammingExerciseTestCase[];
    @Input() testCaseStatsMap?: TestCaseStatsMap;
    @Input() totalParticipations?: number;
    @Input() exercise: ProgrammingExercise;

    @Output() testCaseColorsChange = new EventEmitter<any>();
    @Output() testCaseRowFilter = new EventEmitter<number>();

    readonly testCaseBarTitle = TestCaseBarTitle;

    totalWeight: number;

    // ngx
    // array containing the ngx-dedicated objects in order to display the weight and bonus chart
    ngxWeightData: NgxChartsMultiSeriesDataEntry[] = [
        { name: '', series: [] as any[] },
        { name: '', series: [] as any[] },
    ];
    // array containing the ngx-dedicated objects in order to display the points chart
    ngxPointsData: NgxChartsMultiSeriesDataEntry[] = [{ name: '', series: [] as any[] }];

    constructor(
        private translateService: TranslateService,
        private navigationUtilService: ArtemisNavigationUtilService,
    ) {
        super();

        this.translateService.onLangChange.subscribe(() => {
            this.updateTranslation();
        });
    }

    ngOnInit(): void {
        this.updateTranslation();
    }

    ngOnChanges(): void {
        if (this.testCases == undefined) {
            this.testCases = [];
        }
        this.testCases = this.testCases.filter((testCase) => testCase.visibility !== Visibility.Never);

        // sum of all weights
        this.totalWeight = this.testCases.reduce((sum, testCase) => sum + testCase.weight!, 0);
        // max points for the exercise
        const maxPoints = this.exercise.maxPoints!;
        // exercise max score with bonus in percent
        const maxScoreInPercent = getTotalMaxPoints(this.exercise) / maxPoints;

        // total of achievable points for this exercise
        const totalPoints = maxPoints * (this.totalParticipations || 0);

        const testCaseScores = this.testCases.map((testCase) => {
            // calculated score for this test case
            const testCaseScore = (this.totalWeight > 0 ? (testCase.weight! * testCase.bonusMultiplier!) / this.totalWeight : 0) + (testCase.bonusPoints || 0) / maxPoints;

            const score = Math.min(testCaseScore, maxScoreInPercent);
            const stats = this.testCaseStatsMap ? this.testCaseStatsMap[testCase.testName!] : undefined;

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

        if (this.ngxWeightData[0].series.length !== testCaseScores.length) {
            const testCaseColors: TestCaseColors = {};

            this.ngxWeightData = [];
            this.ngxPointsData = [];

            const weight = { name: this.translateService.instant('artemisApp.programmingExercise.configureGrading.charts.testCaseWeights.weight'), series: [] as any[] };
            const weightAndBonus = {
                name: this.translateService.instant('artemisApp.programmingExercise.configureGrading.charts.testCaseWeights.weightAndBonus'),
                series: [] as any[],
            };

            const points = { name: this.translateService.instant('artemisApp.programmingExercise.configureGrading.charts.testCasePoints.points'), series: [] as any[] };

            for (let i = 0; i < testCaseScores.length; i++) {
                const element = testCaseScores[i];

                const label = element.label;
                const color = getColor(i / this.testCases.length, 50);

                weight.series.push({ name: label, value: Math.max(element.relWeight, 0), bonus: Math.max(element.relScore, 0), id: element.id });
                weightAndBonus.series.push({ name: label, value: Math.max(element.relScore, 0), weight: Math.max(element.relScore, 0), id: element.id });

                points.series.push({ name: label, value: Math.max(element.relPoints, 0) });

                testCaseColors[label] = color;
                this.ngxColors.domain.push(color);
            }

            this.ngxWeightData.push(weight);
            this.ngxWeightData.push(weightAndBonus);

            this.ngxPointsData.push(points);

            // update colors for test case table
            this.testCaseColorsChange.emit(testCaseColors);
        } else {
            // update values in-place
            testCaseScores.forEach((score) => {
                this.ngxWeightData[0].series.forEach((weight, index) => {
                    if (weight.id === score.id) {
                        weight.value = Math.max(score.relWeight, 0);
                        weight.bonus = Math.max(score.relScore, 0);
                        // the bars are set up symmetrically, which means if we have the index of the corresponding test case in one bar, it is the same for all other bars
                        this.ngxWeightData[1].series[index].value = Math.max(score.relScore, 0);
                        this.ngxWeightData[1].series[index].weight = Math.max(score.relWeight, 0);
                        this.ngxPointsData[0].series[index].value = Math.max(score.relPoints, 0);
                    }
                });
            });
        }

        this.ngxWeightData = [...this.ngxWeightData];
        this.ngxPointsData = [...this.ngxPointsData];
    }

    /**
     * Auxiliary method that handles the click on the points chart
     * Delegates the user to the statistics page of the programming exercise
     */
    onSelectPoints(): void {
        this.navigationUtilService.routeInNewTab(['course-management', this.exercise.course!.id, 'programming-exercises', this.exercise.id, 'exercise-statistics']);
    }

    /**
     * Auxiliary method that handles the click on the weight and bonus chart
     * Filters the table left to the charts in order to display only the test case that is clicked
     * @param event event that is delegated by ngx-charts and contains the test case ID
     */
    onSelectWeight(event: any): void {
        this.tableFiltered = true;
        this.testCaseRowFilter.emit(event.id as number);
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

        this.ngxWeightData[0].name = weightLabel;
        this.ngxWeightData[1].name = weightAndBonusLabel;
        this.ngxPointsData[0].name = pointsLabel;

        this.ngxWeightData = [...this.ngxWeightData];
        this.ngxPointsData = [...this.ngxPointsData];
    }
}
