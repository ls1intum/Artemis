import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { StaticCodeAnalysisCategory, StaticCodeAnalysisCategoryState } from 'app/entities/static-code-analysis-category.model';
import { HorizontalStackedBarChartPreset } from 'app/shared/chart/presets/horizontalStackedBarChartPreset';
import { ChartDataSets } from 'chart.js';

@Component({
    selector: 'jhi-sca-category-distribution-chart',
    template: `
        <div>
            <div>
                <h4>Category Distribution</h4>
                <p>The distribution of categories across the metrices 'Penalty', 'Issues' and 'Points'. Hover over a colored block to see the category details.</p>
            </div>
            <div class="bg-light">
                <jhi-chart [preset]="chartPreset" [datasets]="chartDatasets"></jhi-chart>
            </div>
        </div>
    `,
})
export class ScaCategoryDistributionChartComponent implements OnChanges {
    @Input() categories: StaticCodeAnalysisCategory[];
    @Input() categoryHitMap?: { [category: string]: number }[];
    @Input() totalParticipations?: number;
    @Input() exercise: ProgrammingExercise;

    @Input() categoryColors = {};
    @Output() categoryColorsChange = new EventEmitter<{}>();

    chartPreset = new HorizontalStackedBarChartPreset(['Penalty', 'Issues', 'Deductions'], ['all penalties', 'all detected issues', 'all deducted points']);
    chartDatasets: ChartDataSets[] = [];

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

        this.chartDatasets = categoryPenalties.map((element, i) => ({
            label: element.category.name,
            data: [
                (totalPenalty > 0 ? Math.min(element.category.penalty, element.category.maxPenalty) / totalPenalty : 0) * 100,
                (totalIssues > 0 ? element.issues / totalIssues : 0) * 100,
                (totalPenaltyPoints > 0 ? element.penalty / totalPenaltyPoints : 0) * 100,
            ],
            backgroundColor: this.getColor(i / this.categories.length, 50),
            hoverBackgroundColor: this.getColor(i / this.categories.length, 60),
        }));

        this.categoryColors = {};
        this.chartDatasets.forEach(({ label, backgroundColor }) => (this.categoryColors[label!] = backgroundColor));
        this.categoryColorsChange.emit(this.categoryColors);
    }

    getColor(i: number, l: number): string {
        return `hsl(${(i * 360 * 3) % 360}, 55%, ${l}%)`;
    }
}
