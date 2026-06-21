import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { StaticCodeAnalysisCategory, StaticCodeAnalysisCategoryState } from 'app/programming/shared/entities/static-code-analysis-category.model';
import { CategoryIssuesMap } from 'app/programming/shared/entities/programming-exercise-test-case-statistics.model';
import { TranslateService } from '@ngx-translate/core';
import { getColor } from 'app/programming/manage/grading/charts/programming-grading-charts.utils';
import { ProgrammingGradingChartsDirective } from 'app/programming/manage/grading/charts/programming-grading-charts.directive';
import { ChartMultiSeriesEntry } from 'app/shared-ui/chart/chart-data.model';
import { ChartColorService } from 'app/shared-ui/chart/chart-color.service';
import { multiSeriesToNormalizedStackedBarData } from 'app/shared-ui/chart/chart-adapters';
import { barChartOptions, toChartSelectEvent } from 'app/shared-ui/chart/chart-options';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ChartModule } from 'primeng/chart';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-sca-category-distribution-chart',
    styleUrls: ['./sca-category-distribution-chart.scss'],
    template: `
        <div>
            <div>
                <div class="d-flex justify-content-between">
                    <h4 jhiTranslate="artemisApp.programmingExercise.configureGrading.charts.categoryDistribution.title"></h4>
                    @if (tableFiltered) {
                        <button
                            type="button"
                            class="btn btn-info"
                            (click)="resetTableFilter()"
                            jhiTranslate="artemisApp.programmingExercise.configureGrading.charts.resetFilter"
                        ></button>
                    }
                </div>
                <p [innerHTML]="'artemisApp.programmingExercise.configureGrading.charts.categoryDistribution.description' | artemisTranslate"></p>
            </div>
            <div class="chart bg-light">
                <p-chart type="bar" [data]="chartData()" [options]="chartOptions()" (onDataSelect)="onSelect($event)" />
            </div>
        </div>
    `,
    imports: [TranslateDirective, ChartModule, ArtemisTranslatePipe],
})
export class ScaCategoryDistributionChartComponent extends ProgrammingGradingChartsDirective {
    private translateService = inject(TranslateService);
    private navigationUtilsService = inject(ArtemisNavigationUtilService);

    readonly categories = input.required<StaticCodeAnalysisCategory[]>();
    readonly categoryIssuesMap = input<CategoryIssuesMap>();
    readonly exercise = input.required<ProgrammingExercise>();

    readonly categoryColorsChange = output<{ [key: string]: string }>();
    readonly scaCategoryFilter = output<number>();

    readonly entries = signal<ChartMultiSeriesEntry[]>([]);

    private readonly resolvedColors = inject(ChartColorService).resolvedColors(() => this.chartColors());

    readonly chartData = computed(() => multiSeriesToNormalizedStackedBarData(this.entries(), this.resolvedColors()));
    readonly chartOptions = computed(() =>
        barChartOptions({
            horizontal: true,
            stacked: true,
            percentScale: true,
            xAxis: { tickFormatter: this.xAxisFormatting },
            tooltip: {
                title: (items) => items[0]?.dataset.label ?? '',
                label: (item) => {
                    const meta = item.dataset.meta?.[item.dataIndex];
                    if (!meta) {
                        return '';
                    }
                    // bar 0: penalty, bar 1: issues, bar 2: deductions — value carries the bar's own metric
                    const penalty = ((item.dataIndex === 0 ? meta.value : (meta.penalty as number)) ?? 0).toFixed(2);
                    const issues = ((item.dataIndex === 1 ? meta.value : (meta.issues as number)) ?? 0).toFixed(2);
                    const deductions = ((item.dataIndex === 2 ? meta.value : (meta.points as number)) ?? 0).toFixed(2);
                    return [
                        this.translateService.instant('artemisApp.programmingAssessment.penaltyTooltip', { percentage: penalty }),
                        this.translateService.instant('artemisApp.programmingAssessment.issuesTooltip', { percentage: issues }),
                        this.translateService.instant('artemisApp.programmingAssessment.deductionsTooltip', { percentage: deductions }),
                    ];
                },
            },
        }),
    );

    constructor() {
        super();
        const translateService = this.translateService;

        translateService.onLangChange.subscribe(() => {
            this.updateTranslations();
        });

        effect(() => {
            this.computeChartData();
        });
    }

    private computeChartData(): void {
        const categories = this.categories();
        const categoryIssuesMap = this.categoryIssuesMap();
        const exercise = this.exercise();

        const newEntries: ChartMultiSeriesEntry[] = [];
        const colors: string[] = [];
        // update colors for category table
        const categoryColors: { [key: string]: string } = {};
        const categoryPenalties = categories
            .map((category) => ({
                ...category,
                penalty: category.state === StaticCodeAnalysisCategoryState.Graded ? category.penalty : 0,
                maxPenalty: category.state === StaticCodeAnalysisCategoryState.Graded ? category.maxPenalty : 0,
            }))
            .map((category) => {
                const issuesMap = categoryIssuesMap ? categoryIssuesMap[category.name] || {} : {};

                // total number of issues in this category
                const issuesSum = Object.entries(issuesMap).reduce((sum, [issues, students]) => sum + parseInt(issues, 10) * students, 0);

                // total number of penalty points in this category
                let penaltyPointsSum = Object.entries(issuesMap)
                    .map(([issues, students]) => students * Math.min(parseInt(issues, 10) * category.penalty, category.maxPenalty))
                    .reduce((sum, penaltyPoints) => sum + penaltyPoints, 0);

                if ((exercise.maxStaticCodeAnalysisPenalty || Infinity) < penaltyPointsSum) {
                    penaltyPointsSum = exercise.maxStaticCodeAnalysisPenalty!;
                }

                return { category, issues: issuesSum || 0, penaltyPoints: penaltyPointsSum };
            });

        // sum of all penalties
        const totalPenalty = categoryPenalties.reduce((sum, { category }) => sum + Math.min(category.penalty, category.maxPenalty), 0);
        // sum of all issues
        const totalIssues = categoryPenalties.reduce((sum, { issues }) => sum + issues, 0);
        // sum of all penalty points
        const totalPenaltyPoints = categoryPenalties.reduce((sum, { penaltyPoints }) => sum + penaltyPoints, 0);

        const penalty: ChartMultiSeriesEntry = { name: this.translateService.instant('artemisApp.programmingAssessment.penalty'), series: [] };
        const issue: ChartMultiSeriesEntry = { name: this.translateService.instant('artemisApp.programmingAssessment.issues'), series: [] };
        const deductions: ChartMultiSeriesEntry = { name: this.translateService.instant('artemisApp.programmingAssessment.deductions'), series: [] };

        categoryPenalties.forEach((element, index) => {
            const penaltyScore = totalPenalty > 0 ? Math.max((Math.min(element.category.penalty, element.category.maxPenalty) / totalPenalty) * 100, 0) : 0;
            const issuesScore = totalIssues > 0 ? Math.max((element.issues / totalIssues) * 100, 0) : 0;
            const penaltyPoints = totalPenaltyPoints > 0 ? Math.max((element.penaltyPoints / totalPenaltyPoints) * 100, 0) : 0;
            const color = getColor(index / categories.length, 50);

            penalty.series.push({ name: element.category.name, value: penaltyScore, issues: issuesScore, points: penaltyPoints, isPenalty: true, id: element.category.id });
            issue.series.push({ name: element.category.name, value: issuesScore, penalty: penaltyScore, points: penaltyPoints });
            deductions.series.push({ name: element.category.name, value: penaltyPoints, penalty: penaltyScore, issues: issuesScore });

            colors.push(color);
            categoryColors[element.category.name] = color;
        });
        newEntries.push(penalty);
        newEntries.push(issue);
        newEntries.push(deductions);
        this.chartColors.set(colors);
        this.entries.set(newEntries);

        this.categoryColorsChange.emit(categoryColors);
    }

    /**
     * Handles the click on a specific category in a specific line of the chart
     * If the user clicks a category within the penalty bar, the user is delegated to the scores page of the exercise
     * If the user clicks a category within one of the other two bars, the corresponding table is filtered in order to show this category
     * @param event the event delegated by ngx-charts after the user clicked a part of the chart
     */
    onSelect(event: any): void {
        const selected = toChartSelectEvent(event, this.chartData());
        if (!selected?.meta?.['isPenalty']) {
            const exercise = this.exercise();
            this.navigationUtilsService.routeInNewTab(['course-management', exercise.course!.id, 'programming-exercises', exercise.id, 'scores']);
        } else {
            this.tableFiltered = true;
            this.scaCategoryFilter.emit(selected.meta['id'] as number);
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

        this.entries.update((entries) => entries.map((category, index) => ({ name: labels[index], series: category.series })));
    }
}
