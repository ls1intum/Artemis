import { AfterViewInit, Component, Input, OnChanges, ViewChild } from '@angular/core';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ChartComponent } from 'app/shared/chart/chart.component';
import { TestCaseStats } from 'app/entities/programming-exercise-test-case-statistics.model';

export class GraphSection {
    label: string;
    color: string;
    c1: GraphColumn;
    c2: GraphColumn;
    c3: GraphColumn;
    hovered?: boolean;
}
export class GraphColumn {
    x: number;
    width: number;
}

export interface DataSet {
    data: Array<number>;
    label: string;
}

@Component({
    selector: 'jhi-test-case-distribution-graph-2',
    template: `
        <div>
            <div>
                <h4>Test Case Distribution</h4>
                <p>The distribution of test cases across the metrices 'Weight', 'Weight + Bonus' and 'Points'. Hover over a colored block to see the test-case details.</p>
            </div>
            <div class="bg-light">
                <jhi-chart type="horizontalBar"></jhi-chart>
            </div>
        </div>
    `,
})
export class TestCaseDistributionGraph2Component implements AfterViewInit, OnChanges {
    @ViewChild(ChartComponent) chart: ChartComponent;

    @Input() testCases: ProgrammingExerciseTestCase[];
    @Input() testCaseStats?: TestCaseStats[];
    @Input() totalParticipations?: number;
    @Input() exercise: ProgrammingExercise;

    labels: string[] = [];
    data: number[] = [];
    colors: string[] = [];
    chartType = 'horizontalBar';
    datasets: DataSet[] = [];

    sections: GraphSection[];
    maxColumnWidth: number;

    ngAfterViewInit(): void {
        this.chart.setPadding({ bottom: 30 });
        this.chart.setLegend({ position: 'bottom' });
        this.chart.setTooltip({ mode: 'dataset' });

        this.chart.setYAxe(0, { stacked: true });
        this.chart.setXAxe(0, { stacked: true });

        this.chart.setLabels(['Weight', 'Weight & Bonus', 'Points']);
    }

    ngOnChanges(): void {
        if (!this.totalParticipations) {
            return;
        }

        const totalWeight = this.testCases.reduce((sum, testCase) => sum + testCase.weight!, 0);
        const maxScore = (this.exercise.maxScore! + (this.exercise.bonusPoints || 0)) / this.exercise.maxScore!;

        const testCaseScores = this.testCases.map((testCase) => {
            const testCaseScore = (totalWeight > 0 ? (testCase.weight! * testCase.bonusMultiplier!) / totalWeight : 0) + testCase.bonusPoints! / this.exercise.maxScore!;
            return { testCase, score: Math.min(testCaseScore, maxScore), stats: this.testCaseStats?.find((stats) => stats.testName === testCase.testName) };
        });

        const totalScore = testCaseScores.map(({ score, stats }) => (stats ? score * stats.numPassed! : 0)).reduce((sum, points) => sum + points, 0);

        const makeColumn = (width: number, prevSection?: GraphColumn) => ({
            x: (prevSection?.x || 0) + (prevSection?.width || 0),
            width,
        });

        const sections: GraphSection[] = testCaseScores.reduce((list: GraphSection[], element, i) => {
            const prevSection = list.length > 0 ? list[list.length - 1] : null;
            return [
                ...list,
                {
                    label: element.testCase.testName!,
                    color: this.getColor(i / this.testCases.length),
                    c1: makeColumn((totalWeight > 0 ? element.testCase.weight! / totalWeight : 0) * 100, prevSection?.c1),
                    c2: makeColumn(element.score * 100, prevSection?.c2),
                    c3: makeColumn(element.stats && totalScore > 0 ? ((element.stats.numPassed! * element.score) / totalScore) * 100 : 0, prevSection?.c3),
                },
            ];
        }, []);

        setTimeout(() => {
            if (sections.length > 0) {
                const { c1, c2, c3 } = sections[sections.length - 1];
                this.maxColumnWidth = [c1, c2, c3].map((c) => c.x + c.width).reduce((max, w) => Math.max(max, w), 0);
            } else {
                this.maxColumnWidth = 0;
            }

            sections.forEach((section, i) =>
                this.chart.updateDataset(i, {
                    label: section.label,
                    data: [section.c1.width, section.c2.width, section.c3.width],
                    backgroundColor: section.color,
                }),
            );
        });
    }

    getColor(i: number): string {
        return `hsl(${(i * 360 * 3) % 360}, 55%, 55%)`;
    }
}
