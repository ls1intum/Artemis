import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { TestCaseStatsMap } from 'app/entities/programming-exercise-test-case-statistics.model';
import { HorizontalStackedBarChartPreset } from 'app/shared/chart/presets/horizontalStackedBarChartPreset';
import { ChartDataSets } from 'chart.js';

@Component({
    selector: 'jhi-test-case-distribution-chart',
    template: `
        <div>
            <div>
                <h4>Weight Distribution</h4>
                <p>The distribution of test case weights in the exercise. <br/> Hover over a colored block to see the test-case details.</p>
            </div>
            <div class="bg-light">
                <jhi-chart [preset]="weightChartPreset" [datasets]="weightChartDatasets"></jhi-chart>
            </div>
            <div class="mt-4">
                <h4>Total Points</h4>
                <p>The percentage of points given to students according to a specific test case. <br/> Hover over a colored block to see the test-case details.</p>
            </div>
            <div class="bg-light" style="height: 100px">
                <jhi-chart [preset]="pointsChartPreset" [datasets]="pointsChartDatasets"></jhi-chart>
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

    weightChartPreset = new HorizontalStackedBarChartPreset(['Weight', 'Weight & Bonus'], ['all weights', 'all weights and bonuses']);
    pointsChartPreset = new HorizontalStackedBarChartPreset(['Points'], ['all exercise points']);

    weightChartDatasets: ChartDataSets[] = [];
    pointsChartDatasets: ChartDataSets[] = [];

    ngOnChanges(): void {
        // sum of all weights
        const totalWeight = this.testCases.reduce((sum, testCase) => sum + testCase.weight!, 0);
        // max points for the exercise - 100 for an zero points exercise to still show the graph
        const maxPoints = this.exercise.maxScore! + (this.exercise.bonusPoints || 0) === 0 ? 100 : this.exercise.maxScore!;
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

        if (this.weightChartDatasets.length != testCaseScores.length) {

            const testCaseColors = {};

            this.weightChartDatasets = [];
            this.pointsChartDatasets = [];

            for (let i in testCaseScores) {
                const element = testCaseScores[i];

                const label = element.label;
                const backgroundColor = this.getColor(+i / this.testCases.length, 50);
                const hoverBackgroundColor = this.getColor(+i / this.testCases.length, 60);

                testCaseColors[label] = backgroundColor;

                this.weightChartDatasets.push({
                    label, backgroundColor, hoverBackgroundColor, data: [
                        element.relWeight,
                        element.relScore,
                    ],
                });

                this.pointsChartDatasets.push({
                    label, backgroundColor, hoverBackgroundColor, data: [
                        element.relPoints,
                    ],
                });
            }

            // update colors for test case table
            this.testCaseColorsChange.emit(testCaseColors);
        } else {
            
            // update values in-place
            for (let i in testCaseScores) {
                const element = testCaseScores[i];
                this.weightChartDatasets[i].data![0] = element.relWeight;
                this.weightChartDatasets[i].data![1] = element.relScore;
                this.pointsChartDatasets[i].data![0] = element.relPoints;
            }
        }
    }

    getColor(i: number, l: number): string {
        return `hsl(${(i * 360 * 3) % 360}, 55%, ${l}%)`;
    }
}
