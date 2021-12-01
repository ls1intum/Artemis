import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { ProgrammingExerciseTestCase, Visibility } from 'app/entities/programming-exercise-test-case.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TestCaseStatsMap } from 'app/entities/programming-exercise-test-case-statistics.model';
import { TranslateService } from '@ngx-translate/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';

@Component({
    selector: 'jhi-test-case-distribution-chart',
    template: `
        <div>
            <div>
                <h4>{{ 'artemisApp.programmingExercise.configureGrading.charts.testCaseWeights.title' | artemisTranslate }}</h4>
                <p [innerHTML]="'artemisApp.programmingExercise.configureGrading.charts.testCaseWeights.description' | artemisTranslate"></p>
            </div>
            <div #containerRefWeight class="bg-light">
                <!--<jhi-chart [preset]="weightChartPreset" [datasets]="weightChartDatasets"></jhi-chart>-->
                <ngx-charts-bar-horizontal-normalized
                    [view]="[containerRefWeight.offsetWidth, 130]"
                    [results]="ngxWeightData"
                    [xAxis]="true"
                    [yAxis]="true"
                    [xAxisTickFormatting]="xAxisFormatting"
                    [scheme]="ngxColors"
                >
                    <ng-template #tooltipTemplate let-model="model">
                        <b>{{ model.name }}</b>
                        <br />
                        <div *ngIf="['Weight', 'Gewichtung'].includes(model.series)">
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
                        <div *ngIf="['Weight & Bonus', 'Gewichtung & Bonus'].includes(model.series)">
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
            <div #containerRefPoints class="bg-light" style="height: 100px">
                <!--<jhi-chart [preset]="pointsChartPreset" [datasets]="pointsChartDatasets"></jhi-chart>-->
                <ngx-charts-bar-horizontal-stacked
                    [view]="[containerRefPoints.offsetWidth, 100]"
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

    ngxWeightData: any[] = [
        { name: this.translateService.instant('artemisApp.programmingExercise.configureGrading.charts.testCaseWeights.weight'), series: [] as any[] },
        { name: this.translateService.instant('artemisApp.programmingExercise.configureGrading.charts.testCaseWeights.weightAndBonus'), series: [] as any[] },
    ];
    ngxPointsData: any[] = [{ name: this.translateService.instant('artemisApp.programmingExercise.configureGrading.charts.testCasePoints.points'), series: [] as any[] }];

    ngxData: any[] = [];
    ngxColors = {
        name: 'test case distribution',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    } as Color;

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

        if (this.ngxWeightData[0].length !== testCaseScores.length) {
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
                const backgroundColor = this.getColor(+i / this.testCases.length, 50);
                const color = this.hslToHex(backgroundColor);
                // const hoverBackgroundColor = this.getColor(+i / this.testCases.length, 60);

                weight.series.push({ name: element.label, value: element.relWeight, bonus: element.relScore });
                weightAndBonus.series.push({ name: element.label, value: element.relScore, weight: element.relWeight });

                points.series.push({ name: element.label, value: element.relPoints });
                testCaseColors[label] = color;
                this.ngxColors.domain.push(color);

                /*this.weightChartDatasets.push({
                    label,
                    backgroundColor,
                    hoverBackgroundColor,
                    data: [element.relWeight, element.relScore],
                });

                this.pointsChartDatasets.push({
                    label,
                    backgroundColor,
                    hoverBackgroundColor,
                    data: [element.relPoints],
                });*/
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
                /*this.weightChartDatasets[i].data![0] = element.relWeight;
                this.weightChartDatasets[i].data![1] = element.relScore;
                this.pointsChartDatasets[i].data![0] = element.relPoints;*/

                this.ngxWeightData[0].series[i].value = element.relWeight;
                this.ngxWeightData[1].series[i].value = element.relScore;
                this.ngxPointsData[0].series[i].value = element.relPoints;
            }
        }

        this.ngxWeightData = [...this.ngxWeightData];
        this.ngxPointsData = [...this.ngxPointsData];
    }

    getColor(i: number, l: number): number[] {
        return [(i * 360 * 3) % 360, 55, l];
        // return `hsl(${(i * 360 * 3) % 360}, 55%, ${l}%)`;
    }

    xAxisFormatting(value: any) {
        return value + '%';
    }

    private hslToHex(hsl: number[]): string {
        const h = hsl[0];
        const s = hsl[1];
        let l = hsl[2];
        l /= 100;
        const a = (s * Math.min(l, 1 - l)) / 100;
        const f = (n: number) => {
            const k = (n + h / 30) % 12;
            const color = l - a * Math.max(Math.min(k - 3, 9 - k, 1), -1);
            return Math.round(255 * color)
                .toString(16)
                .padStart(2, '0'); // convert to Hex and prefix "0" if needed
        };
        return `#${f(0)}${f(8)}${f(4)}`;
    }
}
