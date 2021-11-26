import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { StaticCodeAnalysisCategory, StaticCodeAnalysisCategoryState } from 'app/entities/static-code-analysis-category.model';
import { HorizontalStackedBarChartPreset } from 'app/shared/chart/presets/horizontalStackedBarChartPreset';
import { ChartDataSets } from 'chart.js';
import { CategoryIssuesMap } from 'app/entities/programming-exercise-test-case-statistics.model';
import { Color, ScaleType } from '@swimlane/ngx-charts';

@Component({
    selector: 'jhi-sca-category-distribution-chart',
    template: `
        <div>
            <div>
                <h4>{{ 'artemisApp.programmingExercise.configureGrading.charts.categoryDistribution.title' | artemisTranslate }}</h4>
                <p [innerHTML]="'artemisApp.programmingExercise.configureGrading.charts.categoryDistribution.description' | artemisTranslate"></p>
            </div>
            <div #containerRef class="bg-light">
                <!--<jhi-chart [preset]="chartPreset" [datasets]="chartDatasets" [ngxData]="ngxData"></jhi-chart>-->
                <ngx-charts-bar-horizontal-normalized
                    [view]="[containerRef.offsetWidth, 200]"
                    [scheme]="ngxColors"
                    [results]="ngxData"
                    [xAxis]="true"
                    [yAxis]="true"
                    [xAxisTickFormatting]="xAxisFormatting"
                >
                    <ng-template #tooltipTemplate let-model="model">
                        <h6>{{ model.name }}</h6>
                        <div [ngSwitch]="model.series">
                            <span *ngSwitchCase="'Penalty'">
                                {{ model.series }}: {{ 'artemisApp.programmingAssessment.penalitesTooltip' | artemisTranslate: { percentage: model.value.toFixed(2) } }}
                            </span>
                            <span *ngSwitchCase="'Issues'">
                                {{ model.series }}: {{ 'artemisApp.programmingAssessment.issuesTooltip' | artemisTranslate: { percentage: model.value.toFixed(2) } }}
                            </span>
                            <span *ngSwitchCase="'Deductions'">
                                {{ model.series }}: {{ 'artemisApp.programmingAssessment.deductionsTooltip' | artemisTranslate: { percentage: model.value.toFixed(2) } }}
                            </span>
                        </div>
                    </ng-template>
                </ngx-charts-bar-horizontal-normalized>
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
    ngxData: any[] = [];
    ngxColors = {
        name: 'category distribution',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    } as Color;

    ngOnChanges(): void {
        this.ngxData = [];
        this.ngxColors.domain = [];
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

        const penalty = { name: 'Penalty', series: [] as any[] };
        const issue = { name: 'Issues', series: [] as any[] };
        const deductions = { name: 'Deductions', series: [] as any[] };

        categoryPenalties.forEach((element, index) => {
            const penaltyScore = totalPenalty > 0 ? (Math.min(element.category.penalty, element.category.maxPenalty) / totalPenalty) * 100 : 0;
            const issuesScore = totalIssues > 0 ? (element.issues / totalIssues) * 100 : 0;
            const penaltyPoints = totalPenaltyPoints > 0 ? (element.penaltyPoints / totalPenaltyPoints) * 100 : 0;
            penalty.series.push({ name: element.category.name, value: penaltyScore });
            issue.series.push({ name: element.category.name, value: issuesScore });
            deductions.series.push({ name: element.category.name, value: penaltyPoints });
            this.ngxColors.domain.push(this.hslToHex((index / this.categories.length) * 360 * 3, 55, 50));
        });
        this.ngxData.push(penalty);
        this.ngxData.push(issue);
        this.ngxData.push(deductions);
        this.ngxData = [...this.ngxData];
        // update colors for category table
        const categoryColors = {};
        this.chartDatasets.forEach(({ label, backgroundColor }) => (categoryColors[label!] = backgroundColor));
        this.categoryColorsChange.emit(categoryColors);
    }

    getColor(i: number, l: number): string {
        return `hsl(${(i * 360 * 3) % 360}, 55%, ${l}%)`;
    }

    private hslToHex(h: number, s: number, l: number): string {
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

    stringify(value: any) {
        return JSON.stringify(value);
    }

    xAxisFormatting(value: any): string {
        return value + '%';
    }
}
