import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { StaticCodeAnalysisCategory, StaticCodeAnalysisCategoryState } from 'app/entities/static-code-analysis-category.model';
import { CategoryIssuesMap } from 'app/entities/programming-exercise-test-case-statistics.model';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { TranslateService } from '@ngx-translate/core';
import { getColor, xAxisFormatting } from 'app/exercises/programming/manage/grading/charts/programming-grading-charts.utils';

enum ScaChartBarTitle {
    PENALTY = 'Penalty',
    ISSUES = 'Issues',
    DEDUCTIONS_EN = 'Deductions',
    DEDUCTIONS_DE = 'Punkte',
}

@Component({
    selector: 'jhi-sca-category-distribution-chart',
    styleUrls: ['./sca-category-distribution-chart.scss'],
    template: `
        <div>
            <div>
                <h4>{{ 'artemisApp.programmingExercise.configureGrading.charts.categoryDistribution.title' | artemisTranslate }}</h4>
                <p [innerHTML]="'artemisApp.programmingExercise.configureGrading.charts.categoryDistribution.description' | artemisTranslate"></p>
            </div>
            <div #containerRef class="chart bg-light">
                <ngx-charts-bar-horizontal-normalized
                    [view]="[containerRef.offsetWidth, containerRef.offsetHeight]"
                    [scheme]="ngxColors"
                    [results]="ngxData"
                    [xAxis]="true"
                    [yAxis]="true"
                    [xAxisTickFormatting]="xAxisFormatting"
                >
                    <ng-template #tooltipTemplate let-model="model">
                        <b>{{ model.name }}</b>
                        <br />
                        <div [ngSwitch]="model.series">
                            <div *ngSwitchCase="scaChartBarTitle.PENALTY">
                                <span>
                                    {{ 'artemisApp.programmingAssessment.penaltyTooltip' | artemisTranslate: { percentage: model.value.toFixed(2) } }}
                                </span>
                                <br />
                                <span>
                                    {{ 'artemisApp.programmingAssessment.issuesTooltip' | artemisTranslate: { percentage: model.issues.toFixed(2) } }}
                                </span>
                                <br />
                                <span>
                                    {{ 'artemisApp.programmingAssessment.deductionsTooltip' | artemisTranslate: { percentage: model.points.toFixed(2) } }}
                                </span>
                            </div>
                            <div *ngSwitchCase="scaChartBarTitle.ISSUES">
                                <span>
                                    {{ 'artemisApp.programmingAssessment.penaltyTooltip' | artemisTranslate: { percentage: model.penalty.toFixed(2) } }}
                                </span>
                                <br />
                                <span>
                                    {{ 'artemisApp.programmingAssessment.issuesTooltip' | artemisTranslate: { percentage: model.value.toFixed(2) } }}
                                </span>
                                <br />
                                <span>
                                    {{ 'artemisApp.programmingAssessment.deductionsTooltip' | artemisTranslate: { percentage: model.points.toFixed(2) } }}
                                </span>
                            </div>
                        </div>
                        <div *ngIf="[scaChartBarTitle.DEDUCTIONS_EN, scaChartBarTitle.DEDUCTIONS_DE].includes(model.series)">
                            <span>
                                {{ 'artemisApp.programmingAssessment.penaltyTooltip' | artemisTranslate: { percentage: model.penalty.toFixed(2) } }}
                            </span>
                            <br />
                            <span>
                                {{ 'artemisApp.programmingAssessment.issuesTooltip' | artemisTranslate: { percentage: model.issues.toFixed(2) } }}
                            </span>
                            <br />
                            <span>
                                {{ 'artemisApp.programmingAssessment.deductionsTooltip' | artemisTranslate: { percentage: model.value.toFixed(2) } }}
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

    readonly scaChartBarTitle = ScaChartBarTitle;

    // ngx
    ngxData: any[] = [];
    ngxColors = {
        name: 'category distribution',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    } as Color;

    readonly xAxisFormatting = xAxisFormatting;

    constructor(private translateService: TranslateService) {}

    ngOnChanges(): void {
        this.ngxData = [];
        this.ngxColors.domain = [];
        // update colors for category table
        const categoryColors = {};
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

        const penalty = { name: this.translateService.instant('artemisApp.programmingAssessment.penalty'), series: [] as any[] };
        const issue = { name: this.translateService.instant('artemisApp.programmingAssessment.issues'), series: [] as any[] };
        const deductions = { name: this.translateService.instant('artemisApp.programmingAssessment.deductions'), series: [] as any[] };

        categoryPenalties.forEach((element, index) => {
            const penaltyScore = totalPenalty > 0 ? Math.max((Math.min(element.category.penalty, element.category.maxPenalty) / totalPenalty) * 100, 0) : 0;
            const issuesScore = totalIssues > 0 ? Math.max((element.issues / totalIssues) * 100, 0) : 0;
            const penaltyPoints = totalPenaltyPoints > 0 ? Math.max((element.penaltyPoints / totalPenaltyPoints) * 100, 0) : 0;
            const color = getColor(index / this.categories.length, 50);

            penalty.series.push({ name: element.category.name, value: penaltyScore, issues: issuesScore, points: penaltyPoints });
            issue.series.push({ name: element.category.name, value: issuesScore, penalty: penaltyScore, points: penaltyPoints });
            deductions.series.push({ name: element.category.name, value: penaltyPoints, penalty: penaltyScore, issues: issuesScore });

            this.ngxColors.domain.push(color);
            categoryColors[element.category.name] = color;
        });
        this.ngxData.push(penalty);
        this.ngxData.push(issue);
        this.ngxData.push(deductions);
        this.ngxData = [...this.ngxData];

        this.categoryColorsChange.emit(categoryColors);
    }
}
