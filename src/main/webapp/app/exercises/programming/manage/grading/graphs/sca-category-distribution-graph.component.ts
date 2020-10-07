import { Component, Input, OnChanges } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { StaticCodeAnalysisCategory, StaticCodeAnalysisCategoryState } from 'app/entities/static-code-analysis-category.model';
import { GraphColumn, GraphSection } from './test-case-distribution-graph.component';

@Component({
    selector: 'jhi-sca-category-distribution-graph',
    template: `
        <div>
            <div>
                <h4>Category Distribution</h4>
                <p>The distribution of categories across the metrices 'Penalty', 'Issues' and 'Points'. Hover over a colored block to see the category details.</p>
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
                    <text x="-3" y="15" dominant-baseline="central" font-size="3" text-anchor="end">Penalty</text>
                    <text x="-3" y="30" dominant-baseline="central" font-size="3" text-anchor="end">Issues</text>
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
                        <b>{{ hoveredSection!.c1.width.toFixed(2) }}%</b> of the total sum of per-issue penalties, </span
                    ><br />
                    <span>
                        <b>{{ hoveredSection!.c2.width.toFixed(2) }}%</b> of the total number of issues, </span
                    ><br />
                    <span>
                        <b>{{ hoveredSection!.c3.width.toFixed(2) }}%</b> of the current penalty points of all students.
                    </span>
                </div>
            </div>
        </div>
    `,
})
export class ScaCategoryDistributionGraphComponent implements OnChanges {
    @Input() categories: StaticCodeAnalysisCategory[];
    @Input() categoryHitMap?: { [category: string]: number }[];
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

        const categoryPenalties = this.categories
            .map((category) => ({
                ...category,
                penalty: category.state === StaticCodeAnalysisCategoryState.Graded ? category.penalty : 0,
                maxPenalty: category.state === StaticCodeAnalysisCategoryState.Graded ? category.maxPenalty : 0,
            }))
            .map((category) => {
                const issuesSum = this.categoryHitMap?.reduce((sum, issues) => sum + (issues[category.name] || 0), 0);
                let penaltySum = this.categoryHitMap?.reduce((sum, issues) => sum + Math.min((issues[category.name] || 0) * category.penalty, category.maxPenalty), 0);
                penaltySum = Math.min(penaltySum || 0, this.exercise.maxStaticCodeAnalysisPenalty || penaltySum || 0);
                return { category, issues: issuesSum || 0, penalty: penaltySum };
            })
            .filter(({ category, issues }) => category.state !== StaticCodeAnalysisCategoryState.Inactive && (category.penalty !== 0 || issues !== 0));

        const totalPenalty = categoryPenalties.reduce((sum, { category }) => sum + Math.min(category.penalty, category.maxPenalty), 0);
        const totalIssues = categoryPenalties.reduce((sum, { issues }) => sum + issues, 0);
        const totalPenaltyPoints = categoryPenalties.reduce((sum, { penalty }) => sum + penalty, 0);

        const makeColumn = (width: number, prevSection?: GraphColumn) => ({
            x: (prevSection?.x || 0) + (prevSection?.width || 0),
            width,
        });

        const sections: GraphSection[] = categoryPenalties.reduce((list: GraphSection[], element, i) => {
            const prevSection = list.length > 0 ? list[list.length - 1] : null;
            return [
                ...list,
                {
                    label: element.category.name,
                    color: this.getColor(i / this.categories.length),
                    c1: makeColumn((totalPenalty > 0 ? Math.min(element.category.penalty, element.category.maxPenalty) / totalPenalty : 0) * 100, prevSection?.c1),
                    c2: makeColumn((totalIssues > 0 ? element.issues / totalIssues : 0) * 100, prevSection?.c2),
                    c3: makeColumn((totalPenaltyPoints > 0 ? element.penalty / totalPenaltyPoints : 0) * 100, prevSection?.c3),
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
