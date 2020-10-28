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
                <h4>Test Case Distribution</h4>
                <p>The distribution of test cases across the metrices 'Weight', 'Weight + Bonus' and 'Points'. Hover over a colored block to see the test-case details.</p>
            </div>
            <div class="bg-light">
                <jhi-chart [preset]="chartPreset" [datasets]="chartDatasets"></jhi-chart>
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

    chartPreset = new HorizontalStackedBarChartPreset(['Weight', 'Weight & Bonus', 'Points'], ['all weights', 'all weights and bonuses', 'all achieved points']);
    chartDatasets: ChartDataSets[] = [];

    ngOnChanges(): void {
        // sum of all weights
        const totalWeight = this.testCases.reduce((sum, testCase) => sum + testCase.weight!, 0);
        // max points for the exercise - 100 for an zero points exercise to still show the graph
        const maxPoints = this.exercise.maxScore! + (this.exercise.bonusPoints || 0) === 0 ? 100 : this.exercise.maxScore!;
        // exercise max score with bonus in percent
        const maxScoreInPercent = (maxPoints + (this.exercise.bonusPoints || 0)) / maxPoints;

        const testCaseScores = this.testCases.map((testCase) => {
            // calculated score for this test case
            const testCaseScore = (totalWeight > 0 ? (testCase.weight! * testCase.bonusMultiplier!) / totalWeight : 0) + (testCase.bonusPoints || 0) / maxPoints;
            return {
                testCase,
                score: Math.min(testCaseScore, maxScoreInPercent),
                stats: this.testCaseStatsMap ? this.testCaseStatsMap[testCase.testName!] : undefined,
            };
        });

        // total of achievable points for this exercise
        const totalPoints = maxPoints * (this.totalParticipations || 0);

        this.chartDatasets = testCaseScores.map((element, i) => ({
            label: element.testCase.testName!,
            data: [
                // relative weight percentage
                totalWeight > 0 ? (element.testCase.weight! / totalWeight) * 100 : 0,
                // relative score percentage
                element.score * 100,
                // relative points percentage
                element.stats && totalPoints > 0 ? ((element.stats.numPassed! * element.score * maxPoints) / totalPoints) * 100 : 0,
            ],
            backgroundColor: this.getColor(i / this.testCases.length, 50),
            hoverBackgroundColor: this.getColor(i / this.testCases.length, 60),
        }));

        // update colors for test case table
        const testCaseColors = {};
        this.chartDatasets.forEach(({ label, backgroundColor }) => (testCaseColors[label!] = backgroundColor));
        this.testCaseColorsChange.emit(testCaseColors);
    }

    getColor(i: number, l: number): string {
        return `hsl(${(i * 360 * 3) % 360}, 55%, ${l}%)`;
    }
}
