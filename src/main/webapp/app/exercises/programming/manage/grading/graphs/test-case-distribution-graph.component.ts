import { Component, Input, OnChanges } from '@angular/core';
import { TestCaseStats } from 'app/entities/programming-exercise-test-case-statistics.model';
import { ProgrammingExerciseTestCase } from 'app/entities/programming-exercise-test-case.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

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

@Component({
    selector: 'jhi-test-case-distribution-graph',
    template: `
        <div>
            <div>
                <h4>Test Case Distribution</h4>
                <p>The distribution of test cases across the metrices 'Weight', 'Weight + Bonus' and 'Points'. Hover over a colored block to see the test-case details.</p>
            </div>
            <div class="bg-light">
                <svg [attr.viewBox]="'-17 -30 ' + (maxColumnWidth + 42) + ' 90'" style="max-height: 250px">
                    <line x1="-1" x2="-1" y1="6" y2="51" stroke="#555" stroke-width="1"></line>
                    <line x1="-1" [attr.x2]="maxColumnWidth + 10" y1="51" y2="51" stroke="#555" stroke-width="1"></line>
                    <line x1="101" x2="101" y1="6" y2="51" stroke="#aaa" stroke-dasharray="2 2" stroke-width="1"></line>
                    <g *ngFor="let section of sections" (mouseenter)="section.hovered = true" (mouseleave)="section.hovered = false">
                        <text
                            [attr.x]="section.c1.x + section.c1.width / 2"
                            y="5"
                            font-size="3"
                            dominant-baseline="central"
                            [style]="{ transformOrigin: 'left', transform: 'rotate(-45deg)', transformBox: 'fill-box', fontWeight: section.hovered ? 'bold' : 'normal' }"
                        >
                            {{ section.label }}
                        </text>
                        <rect
                            [attr.x]="section.c1.x"
                            [attr.width]="section.c1.width - (section.hovered ? 1 : 0)"
                            [attr.fill]="section.color"
                            [attr.stroke]="section.hovered ? 'white' : section.color"
                            stroke-width="1"
                            y="10"
                            height="10"
                        ></rect>
                        <rect
                            [attr.x]="section.c2.x"
                            [attr.width]="section.c2.width - (section.hovered ? 1 : 0)"
                            [attr.fill]="section.color"
                            [attr.stroke]="section.hovered ? 'white' : section.color"
                            stroke-width="1"
                            y="25"
                            height="10"
                        ></rect>
                        <rect
                            [attr.x]="section.c3.x"
                            [attr.width]="section.c3.width - (section.hovered ? 1 : 0)"
                            [attr.fill]="section.color"
                            [attr.stroke]="section.hovered ? 'white' : section.color"
                            stroke-width="1"
                            y="40"
                            height="10"
                        ></rect>
                    </g>
                    <text x="-3" y="15" dominant-baseline="central" font-size="3" text-anchor="end">Weights</text>
                    <text x="-3" y="28" dominant-baseline="central" font-size="3" text-anchor="end">Weights</text>
                    <text x="-3" y="32" dominant-baseline="central" font-size="3" text-anchor="end">&amp; Bonus</text>
                    <text x="-3" y="45" dominant-baseline="central" font-size="3" text-anchor="end">Points</text>
                    <text x="101" y="55" font-size="3" text-anchor="middle">100%</text>
                </svg>
            </div>
            <div class="mt-2 bg-light d-flex justify-content-center align-items-center" style="height: 140px">
                <h6 *ngIf="!hoveredSection">Hover over a block.</h6>
                <div *ngIf="hoveredSection">
                    <h5>{{ hoveredSection!.label }}</h5>
                    <span>accounts for</span>
                    <br />
                    <span>
                        <b>{{ hoveredSection!.c1.width?.toFixed(2) }}%</b> of the total score by weights only, </span
                    ><br />
                    <span>
                        <b>{{ hoveredSection!.c2.width?.toFixed(2) }}%</b> of the total score by weights and bonus points, </span
                    ><br />
                    <span>
                        <b>{{ hoveredSection!.c3.width?.toFixed(2) }}%</b> of the current points of all students.
                    </span>
                </div>
            </div>
        </div>
    `,
})
export class TestCaseDistributionGraphComponent implements OnChanges {
    @Input() testCases: ProgrammingExerciseTestCase[];
    @Input() testCaseStats?: TestCaseStats[];
    @Input() totalParticipations?: number;
    @Input() exercise: ProgrammingExercise;

    colors: string[] = [];
    sections: GraphSection[];
    maxColumnWidth: number;

    get hoveredSection() {
        return this.sections?.find((s) => s.hovered);
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
                    c3: makeColumn(element.stats && totalScore > 0 ? ((element.stats!.numPassed! * element.score) / totalScore) * 100 : 0, prevSection?.c3),
                },
            ];
        }, []);

        setTimeout(() => {
            this.sections = sections;
            if (sections.length > 0) {
                const { c1, c2, c3 } = sections[sections.length - 1];
                this.maxColumnWidth = [c1, c2, c3].map((c) => c.x + c.width).reduce((max, w) => Math.max(max, w), 0);
            } else {
                this.maxColumnWidth = 0;
            }
        });
    }

    getColor(i: number): string {
        return `hsl(${(i * 360 * 3) % 360}, 55%, 55%)`;
    }
}
