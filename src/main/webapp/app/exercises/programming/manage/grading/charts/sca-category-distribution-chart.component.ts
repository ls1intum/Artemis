import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { StaticCodeAnalysisCategory, StaticCodeAnalysisCategoryState } from 'app/entities/static-code-analysis-category.model';
import { HorizontalStackedBarChartPreset } from 'app/shared/chart/presets/horizontalStackedBarChartPreset';
import { ChartDataSets } from 'chart.js';
import { CategoryIssuesMap } from 'app/entities/programming-exercise-test-case-statistics.model';

@Component({
    selector: 'jhi-sca-category-distribution-chart',
    template: `
        <div>
            <div>
                <h4>{{ 'artemisApp.programmingExercise.configureGrading.charts.categoryDistribution.title' | translate }}</h4>
                <p [innerHTML]="'artemisApp.programmingExercise.configureGrading.charts.categoryDistribution.description' | translate"></p>
            </div>
            <div class="bg-light">
                <jhi-chart [preset]="chartPreset" [datasets]="chartDatasets"></jhi-chart>
            </div>
        </div>
    `,
})
export class ScaCategoryDistributionChartComponent implements OnChanges {
    @Input() categories: StaticCodeAnalysisCategory[];
    @Input() categoryIssuesMap?: CategoryIssuesMap;
    @Input() exercise: ProgrammingExercise;

    @Output() categoryColorsChange = new EventEmitter<{}>();

    chartPreset = new HorizontalStackedBarChartPreset(['Penalty', 'Issues', 'Deductions'], ['all penalties', 'all detected issues', 'all deducted points']);
    chartDatasets: ChartDataSets[] = [];

    ngOnChanges(): void {
        const categoryPenalties = this.categories
            .map((category) => ({
                ...category,
                penalty: category.state === StaticCodeAnalysisCategoryState.Graded ? category.penalty : 0,
                maxPenalty: category.state === StaticCodeAnalysisCategoryState.Graded ? category.maxPenalty : 0,
            }))
            .map((category) => {
                const issuesMap = this.categoryIssuesMap ? this.categoryIssuesMap[category.name] || {} : {};

                // total number of issues in this category
                const issuesSum = Object.entries(issuesMap).reduce((sum, [issues, students]) => sum + parseInt(issues, 10) * students, 0);

                // total number of penalty points in this category
                let penaltyPointsSum = Object.entries(issuesMap)
                    .map(([issues, students]) => students * Math.min(parseInt(issues, 10) * category.penalty, category.maxPenalty))
                    .reduce((sum, penaltyPoints) => sum + penaltyPoints, 0);

                if ((this.exercise.maxStaticCodeAnalysisPenalty || Infinity) < penaltyPointsSum) {
                    penaltyPointsSum = this.exercise.maxStaticCodeAnalysisPenalty!;
                }

                return { category, issues: issuesSum || 0, penaltyPoints: penaltyPointsSum };
            });

        // sum of all penalties
        const totalPenalty = categoryPenalties.reduce((sum, { category }) => sum + Math.min(category.penalty, category.maxPenalty), 0);
        // sum of all issues
        const totalIssues = categoryPenalties.reduce((sum, { issues }) => sum + issues, 0);
        // sum of all penalty points
        const totalPenaltyPoints = categoryPenalties.reduce((sum, { penaltyPoints }) => sum + penaltyPoints, 0);

        this.chartDatasets = categoryPenalties.map((element, i) => ({
            label: element.category.name,
            data: [
                // relative penalty percentage
                totalPenalty > 0 ? (Math.min(element.category.penalty, element.category.maxPenalty) / totalPenalty) * 100 : 0,
                // relative issues percentage
                totalIssues > 0 ? (element.issues / totalIssues) * 100 : 0,
                // relative penalty points percentage
                totalPenaltyPoints > 0 ? (element.penaltyPoints / totalPenaltyPoints) * 100 : 0,
            ],
            backgroundColor: this.getColor(i / this.categories.length, 50),
            hoverBackgroundColor: this.getColor(i / this.categories.length, 60),
        }));

        // update colors for category table
        const categoryColors = {};
        this.chartDatasets.forEach(({ label, backgroundColor }) => (categoryColors[label!] = backgroundColor));
        this.categoryColorsChange.emit(categoryColors);
    }

    getColor(i: number, l: number): string {
        return `hsl(${(i * 360 * 3) % 360}, 55%, ${l}%)`;
    }
}
