import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { StaticCodeAnalysisCategory, StaticCodeAnalysisCategoryState } from 'app/entities/static-code-analysis-category.model';
import { CategoryIssuesMap } from 'app/entities/programming-exercise-test-case-statistics.model';
import { TranslateService } from '@ngx-translate/core';
import { getColor } from 'app/exercises/programming/manage/grading/charts/programming-grading-charts.utils';
import { ProgrammingGradingChartsDirective } from 'app/exercises/programming/manage/grading/charts/programming-grading-charts.directive';
import { NgxChartsMultiSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';

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
                <div class="d-flex justify-content-between">
                    <h4>{{ 'artemisApp.programmingExercise.configureGrading.charts.categoryDistribution.title' | artemisTranslate }}</h4>
                    <button *ngIf="tableFiltered" type="button" class="btn btn-info" (click)="resetTableFilter()">
                        {{ 'artemisApp.programmingExercise.configureGrading.charts.resetFilter' | artemisTranslate }}
                    </button>
                </div>
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
                    (select)="onSelect($event)"
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
export class ScaCategoryDistributionChartComponent extends ProgrammingGradingChartsDirective implements OnChanges {
    @Input() categories: StaticCodeAnalysisCategory[];
    @Input() categoryIssuesMap?: CategoryIssuesMap;
    @Input() exercise: ProgrammingExercise;

    @Output() categoryColorsChange = new EventEmitter<{}>();
    @Output() scaCategoryFilter = new EventEmitter<number>();

    readonly scaChartBarTitle = ScaChartBarTitle;

    // ngx
    ngxData: NgxChartsMultiSeriesDataEntry[] = [];

    constructor(private translateService: TranslateService, private navigationUtilsService: ArtemisNavigationUtilService) {
        super();
        translateService.onLangChange.subscribe(() => {
            this.updateTranslations();
        });
    }

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

            penalty.series.push({ name: element.category.name, value: penaltyScore, issues: issuesScore, points: penaltyPoints, isPenalty: true, id: element.category.id });
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

    /**
     * Handles the click on a specific category in a specific line of the chart
     * If the user clicks a category within the penalty bar, the user is delegated to the scores page of the exercise
     * If the user clicks a category within one of the other two bars, the corresponding table is filtered in order to show this category
     * @param event the event delegated by ngx-charts after the user clicked a part of the chart
     */
    onSelect(event: any): void {
        if (!event.isPenalty) {
            this.navigationUtilsService.routeInNewTab(['course-management', this.exercise.course!.id, 'programming-exercises', this.exercise.id, 'scores']);
        } else {
            this.tableFiltered = true;
            this.scaCategoryFilter.emit(event.id);
        }
    }

    /**
     * Auxiliary method for the reset button to reset the table view
     */
    resetTableFilter(): void {
        super.resetTableFilter();
        this.scaCategoryFilter.emit(ProgrammingGradingChartsDirective.RESET_TABLE);
    }

    /**
     * Auxiliary method in order to keep the translation of the bar labels up to date
     */
    updateTranslations(): void {
        const penaltyLabel = this.translateService.instant('artemisApp.programmingAssessment.penalty');
        const issueLabel = this.translateService.instant('artemisApp.programmingAssessment.issues');
        const deductionsLabel = this.translateService.instant('artemisApp.programmingAssessment.deductions');

        const labels = [penaltyLabel, issueLabel, deductionsLabel];

        this.ngxData.forEach((category, index) => {
            category.name = labels[index];
        });
        this.ngxData = [...this.ngxData];
    }
}
