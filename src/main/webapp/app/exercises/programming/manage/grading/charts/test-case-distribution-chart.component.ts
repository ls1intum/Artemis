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

    @Input() testCaseColors = {};
    @Output() testCaseColorsChange = new EventEmitter<{}>();

    chartPreset = new HorizontalStackedBarChartPreset(['Weight', 'Weight & Bonus', 'Points'], ['all weights', 'all weights and bonuses', 'all achievable points']);
    chartDatasets: ChartDataSets[] = [];

    ngOnChanges(): void {
        if (!this.totalParticipations) {
            return;
        }

        const totalWeight = this.testCases.reduce((sum, testCase) => sum + testCase.weight!, 0);
        const maxScore = (this.exercise.maxScore! + (this.exercise.bonusPoints || 0)) / this.exercise.maxScore!;

        const testCaseScores = this.testCases.map((testCase) => {
            const testCaseScore = (totalWeight > 0 ? (testCase.weight! * testCase.bonusMultiplier!) / totalWeight : 0) + testCase.bonusPoints! / this.exercise.maxScore!;
            return { testCase, score: Math.min(testCaseScore, maxScore), stats: this.testCaseStatsMap ? this.testCaseStatsMap[testCase.testName!] : undefined };
        });

        const totalScore = testCaseScores.map(({ score, stats }) => (stats ? score * stats.numPassed! : 0)).reduce((sum, points) => sum + points, 0);
        const totalPoints = this.exercise.maxScore! * this.totalParticipations;

        this.chartDatasets = testCaseScores.map((element, i) => ({
            label: element.testCase.testName!,
            data: [
                (totalWeight > 0 ? element.testCase.weight! / totalWeight : 0) * 100,
                element.score * 100,
                element.stats && totalScore > 0 ? ((element.stats.numPassed! * element.score) / totalPoints) * 100 : 0,
            ],
            backgroundColor: this.getColor(i / this.testCases.length, 50),
            hoverBackgroundColor: this.getColor(i / this.testCases.length, 60),
        }));

        this.testCaseColors = {};
        this.chartDatasets.forEach(({ label, backgroundColor }) => (this.testCaseColors[label!] = backgroundColor));
        this.testCaseColorsChange.emit(this.testCaseColors);
    }

    getColor(i: number, l: number): string {
        return `hsl(${(i * 360 * 3) % 360}, 55%, ${l}%)`;
    }
}
