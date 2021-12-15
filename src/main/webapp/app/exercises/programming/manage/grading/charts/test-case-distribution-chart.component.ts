import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { ProgrammingExerciseTestCase, Visibility } from 'app/entities/programming-exercise-test-case.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TestCaseStatsMap } from 'app/entities/programming-exercise-test-case-statistics.model';
import { TranslateService } from '@ngx-translate/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { getColor, xAxisFormatting } from 'app/exercises/programming/manage/grading/charts/programming-grading-charts.utils';

enum TestCaseBarTitle {
    WEIGHT_EN = 'Weight',
    WEIGHT_DE = 'Gewichtung',
    WEIGHT_AND_BONUS_EN = 'Weight & Bonus',
    WEIGHT_AND_BONUS_DE = 'Gewichtung & Bonus',
}

@Component({
    selector: 'jhi-test-case-distribution-chart',
    styleUrls: ['./sca-category-distribution-chart.scss'],
    template: `
        <div>
            <div>
                <h4>{{ 'artemisApp.programmingExercise.configureGrading.charts.testCaseWeights.title' | artemisTranslate }}</h4>
                <p [innerHTML]="'artemisApp.programmingExercise.configureGrading.charts.testCaseWeights.description' | artemisTranslate"></p>
            </div>
            <div #containerRefWeight class="chart bg-light">
                <ngx-charts-bar-horizontal-normalized
                    [view]="[containerRefWeight.offsetWidth, containerRefWeight.offsetHeight]"
                    [results]="ngxWeightData"
                    [xAxis]="true"
                    [yAxis]="true"
                    [xAxisTickFormatting]="xAxisFormatting"
                    [scheme]="ngxColors"
                >
                    <ng-template #tooltipTemplate let-model="model">
                        <b>{{ model.name }}</b>
                        <br />
                        <div *ngIf="[testCaseBarTitle.WEIGHT_EN, testCaseBarTitle.WEIGHT_DE].includes(model.series)">
                            <span>
                                {{
                                    'artemisApp.programmingExercise.configureGrading.charts.testCaseWeights.weightTooltip'
                                        | artemisTranslate: { percentage: model.value.toFixed(2) }
                                }}
                            </span>
                            <br />
                            <span>
                                {{
                                    'artemisApp.programmingExercise.configureGrading.charts.testCaseWeights.weightAndBonusTooltip'
                                        | artemisTranslate: { percentage: model.bonus.toFixed(2) }
                                }}
                            </span>
                        </div>
                        <div *ngIf="[testCaseBarTitle.WEIGHT_AND_BONUS_EN, testCaseBarTitle.WEIGHT_AND_BONUS_DE].includes(model.series)">
                            <span>
                                {{
                                    'artemisApp.programmingExercise.configureGrading.charts.testCaseWeights.weightTooltip'
                                        | artemisTranslate: { percentage: model.weight.toFixed(2) }
                                }}
                            </span>
                            <br />
                            <span>
                                {{
                                    'artemisApp.programmingExercise.configureGrading.charts.testCaseWeights.weightAndBonusTooltip'
                                        | artemisTranslate: { percentage: model.value.toFixed(2) }
                                }}
                            </span>
                        </div>
                    </ng-template>
                </ngx-charts-bar-horizontal-normalized>
            </div>
            <div class="mt-4">
                <h4>{{ 'artemisApp.programmingExercise.configureGrading.charts.testCasePoints.title' | artemisTranslate }}</h4>
                <p [innerHTML]="'artemisApp.programmingExercise.configureGrading.charts.testCasePoints.description' | artemisTranslate"></p>
            </div>
            <div #containerRefPoints class="points-chart bg-light">
                <ngx-charts-bar-horizontal-stacked
                    [view]="[containerRefPoints.offsetWidth, containerRefPoints.offsetHeight]"
                    [results]="ngxPointsData"
                    [xAxis]="true"
                    [yAxis]="true"
                    [xAxisTickFormatting]="xAxisFormatting"
                    [scheme]="ngxColors"
                    [xScaleMax]="100"
                >
                    <ng-template #tooltipTemplate let-model="model">
                        <b>{{ model.name }}</b>
                        <br />
                        <span>
                            {{ 'artemisApp.programmingExercise.configureGrading.charts.testCasePoints.pointsTooltip' | artemisTranslate: { percentage: model.value.toFixed(2) } }}
                        </span>
                    </ng-template>
                </ngx-charts-bar-horizontal-stacked>
            </div>
        </div>
    `,
})
export class TestCaseDistributionChartComponent implements OnChanges {
    @Input() testCases: ProgrammingExerciseTestCase[];
    @Input() testCaseStatsMap?: TestCaseStatsMap;
    @Input() totalParticipations?: number;
    @Input() exercise: ProgrammingExercise;

    @Output() testCaseColorsChange = new EventEmitter<{}>();

    readonly testCaseBarTitle = TestCaseBarTitle;

    // ngx
    ngxWeightData: any[] = [
        { name: this.translateService.instant('artemisApp.programmingExercise.configureGrading.charts.testCaseWeights.weight'), series: [] as any[] },
        { name: this.translateService.instant('artemisApp.programmingExercise.configureGrading.charts.testCaseWeights.weightAndBonus'), series: [] as any[] },
    ];
    ngxPointsData: any[] = [{ name: this.translateService.instant('artemisApp.programmingExercise.configureGrading.charts.testCasePoints.points'), series: [] as any[] }];

    ngxColors = {
        name: 'test case distribution',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    } as Color;

    readonly xAxisFormatting = xAxisFormatting;

    constructor(private translateService: TranslateService) {}

    ngOnChanges(): void {
        if (this.testCases == undefined) {
            this.testCases = [];
        }
        this.testCases = this.testCases.filter((testCase) => testCase.visibility !== Visibility.Never);

        // sum of all weights
        const totalWeight = this.testCases.reduce((sum, testCase) => sum + testCase.weight!, 0);
        // max points for the exercise
        const maxPoints = this.exercise.maxPoints!;
        // exercise max score with bonus in percent
        const maxScoreInPercent = (maxPoints + (this.exercise.bonusPoints || 0)) / maxPoints;

        // total of achievable points for this exercise
        const totalPoints = maxPoints * (this.totalParticipations || 0);

        const testCaseScores = this.testCases.map((testCase) => {
            // calculated score for this test case
            const testCaseScore = (totalWeight > 0 ? (testCase.weight! * testCase.bonusMultiplier!) / totalWeight : 0) + (testCase.bonusPoints || 0) / maxPoints;

            const score = Math.min(testCaseScore, maxScoreInPercent);
            const stats = this.testCaseStatsMap ? this.testCaseStatsMap[testCase.testName!] : undefined;

            return {
                label: testCase.testName!,
                // relative weight percentage
                relWeight: totalWeight > 0 ? (testCase.weight! / totalWeight) * 100 : 0,
                // relative score percentage
                relScore: score * 100,
                // relative points percentage
                relPoints: stats && totalPoints > 0 ? ((stats.numPassed! * score * maxPoints) / totalPoints) * 100 : 0,
            };
        });

        if (this.ngxWeightData[0].series.length !== testCaseScores.length) {
            const testCaseColors = {};

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

                weight.series.push({ name: label, value: Math.max(element.relWeight, 0), bonus: Math.max(element.relScore, 0) });
                weightAndBonus.series.push({ name: label, value: Math.max(element.relScore, 0), weight: Math.max(element.relScore, 0) });

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
            for (let i = 0; i < testCaseScores.length; i++) {
                const element = testCaseScores[i];
                this.ngxWeightData[0].series[i].value = Math.max(element.relWeight, 0);
                this.ngxWeightData[0].series[i].bonus = Math.max(element.relScore, 0);
                this.ngxWeightData[1].series[i].value = Math.max(element.relScore, 0);
                this.ngxWeightData[1].series[i].weight = Math.max(element.relWeight, 0);
                this.ngxPointsData[0].series[i].value = Math.max(element.relPoints, 0);
            }
        }

        this.ngxWeightData = [...this.ngxWeightData];
        this.ngxPointsData = [...this.ngxPointsData];
    }
}
