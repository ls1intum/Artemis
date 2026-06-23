import { Component, HostListener, OnInit, computed, effect, inject, input, output, signal } from '@angular/core';
import { ChartModule } from 'primeng/chart';
import ChartDataLabels from 'chartjs-plugin-datalabels';
import { ChartSeriesEntry } from 'app/shared-ui/chart/chart-data.model';
import { ChartColorService } from 'app/shared-ui/chart/chart-color.service';
import { singleSeriesChartData } from 'app/shared-ui/chart/chart-adapters';
import { barChartOptions, toChartSelectEvent } from 'app/shared-ui/chart/chart-options';
import { GradeType, GradingScale } from 'app/assessment/shared/entities/grading-scale.model';
import { GradingService } from 'app/assessment/manage/grading/grading-service';
import { TranslateService } from '@ngx-translate/core';
import { GradeStep } from 'app/assessment/shared/entities/grade-step.model';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';

interface ChartClickEvent {
    name: string;
    value: number;
    label: string;
}

@Component({
    selector: 'jhi-participant-scores-distribution',
    templateUrl: './participant-scores-distribution.component.html',
    styleUrls: ['./participant-score-distribution.component.scss'],
    imports: [ChartModule, TranslateDirective, HelpIconComponent],
})
export class ParticipantScoresDistributionComponent implements OnInit {
    private gradingService = inject(GradingService);
    private translateService = inject(TranslateService);

    readonly scores = input<number[]>();

    readonly gradeNames = input<string[]>();

    readonly gradingScale = input<GradingScale>();

    readonly isCourseScore = input<boolean>(true);

    readonly dataLabelFormatting = input<(submissionCount: number) => string>();

    readonly scoreToHighlight = input<number>();

    readonly onSelect = output<ChartClickEvent>();

    readonly gradingScaleExists = signal(false);
    readonly isBonus = signal<boolean | undefined>(undefined);
    private entries: ChartSeriesEntry[] = [];
    readonly height = signal(500);

    readonly helpIconTooltip = signal<string>(undefined!);

    readonly binWidth = 5;

    readonly chartEntries = signal<ChartSeriesEntry[]>([]);
    private readonly chartColors = signal<string[]>([]);
    private readonly yScaleMax = signal<number | undefined>(undefined);
    readonly showYAxisLabel = signal(true);
    private readonly yAxisLabel = signal('');
    private readonly xAxisLabel = signal('');
    private backupDomain: string[] = [];

    private readonly resolvedColors = inject(ChartColorService).resolvedColors(() => this.chartColors());

    readonly chartData = computed(() => singleSeriesChartData(this.chartEntries(), this.resolvedColors()));
    readonly chartOptions = computed(() =>
        barChartOptions({
            xAxis: { label: this.xAxisLabel() },
            yAxis: { label: this.showYAxisLabel() ? this.yAxisLabel() : undefined, max: this.yScaleMax() },
            tooltip: {
                title: (items) => items[0]?.label ?? '',
                label: (item) => `${this.translateService.instant('statistics.amountOfStudents')}: ${item.parsed.y}`,
            },
            dataLabels: this.isCourseScore() ? undefined : { formatter: (value) => (this.dataLabelFormatting() ? this.dataLabelFormatting()!(value) : `${value}`) },
        }),
    );
    readonly dataLabelsPlugin = [ChartDataLabels];

    constructor() {
        // Replaces ngOnChanges: recompute the chart whenever any of the data inputs change.
        effect(() => {
            // Track inputs so the effect re-runs on change.
            this.scores();
            this.gradeNames();
            this.gradingScale();
            this.isCourseScore();
            this.scoreToHighlight();
            this.updateChart();
        });
    }

    ngOnInit() {
        this.setupAxisLabels();

        this.translateService.onLangChange.subscribe(() => {
            this.setupAxisLabels();
        });
        this.realignChart();
    }

    updateChart() {
        const gradingScale = this.gradingScale();
        // Recompute derived state from scratch on every run so that clearing the grading scale or toggling
        // isCourseScore cannot leave stale flags/height behind (the effect re-runs on any input change).
        this.gradingScaleExists.set(!!gradingScale);
        this.isBonus.set(gradingScale?.gradeType === GradeType.BONUS);
        // For course scores we keep the larger height (500px); the exam statistics use the old 400px height.
        this.height.set(this.isCourseScore() ? 500 : 400);
        this.createChart();
        this.yScaleMax.set(this.calculateTickMax());
        this.helpIconTooltip.set(this.determineHelpIconTooltip());
        this.highlightScore();
    }

    /**
     * Handles the appearance adaption for smaller screens.
     * If the screen width is below 700px, the y axis label consumes too much space.
     * In this case, the corresponding configuration flag changes so that ngx-charts hides the label.
     */
    @HostListener('window:resize')
    realignChart() {
        if (window.innerWidth < 700 && this.showYAxisLabel()) {
            this.showYAxisLabel.set(false);
        } else if (window.innerWidth > 700 && !this.showYAxisLabel()) {
            this.showYAxisLabel.set(true);
        }
    }

    /**
     * fill ngxData with a default configuration. The assignment of the names is only a placeholder,
     * they will be set to default labels in createChart.
     * If a grading key exists, ngxData gets reset according to it in calculateFilterDependentStatistics.
     * If no grading key exists, this default configuration is presented to the user.
     */
    private generateDefaultChartSetting(): void {
        this.entries = [];
        if (this.gradingScaleExists()) {
            this.gradingScale()!.gradeSteps.forEach((step) => {
                this.entries.push({
                    name: step.gradeName,
                    value: 0,
                });
            });
        } else {
            for (let i = 0; i < 100 / this.binWidth; i++) {
                const entry = {
                    name: i.toString(),
                    value: 0,
                };
                this.entries.push(entry);
            }
        }
        this.createChartLabels();
    }

    /**
     * Creates the chart labels displaying the intervals each bar covers depending on the existence and state of a grading key
     */
    private createChartLabels(): void {
        if (this.gradingScaleExists()) {
            this.gradingScale()!.gradeSteps.forEach((gradeStep, i) => {
                let label = gradeStep.lowerBoundInclusive || i === 0 ? '[' : '(';
                label += `${gradeStep.lowerBoundPercentage},${gradeStep.upperBoundPercentage}`;
                label += gradeStep.upperBoundInclusive || i === 100 ? ']' : ')';
                label += ` {${gradeStep.gradeName}}`;
                this.entries[i].name = label;
            });
        } else {
            for (let i = 0; i < this.entries.length; i++) {
                let label = `[${i * this.binWidth},${(i + 1) * this.binWidth}`;
                label += i === this.entries.length - 1 ? ']' : ')';

                this.entries[i].name = label;
            }
        }

        this.chartEntries.set([...this.entries]);
    }

    /**
     * Find the grade step index for the corresponding grade step to the given percentage or gradeName
     * depending on the value of matchByGradeName
     * @param percentageOrGradeName the percentage which will be mapped to a grade step.
     */
    findGradeStepIndex(percentageOrGradeName: number | string): number {
        return this.scores() ? this.findGradeStepIndexByPercentage(percentageOrGradeName as number) : this.findGradeStepIndexByGradeName(percentageOrGradeName as string);
    }

    /**
     * Find the grade step index for the corresponding grade step to the given percentage
     * @param percentage the percentage which will be mapped to a grade step.
     */
    findGradeStepIndexByPercentage(percentage: number): number {
        let index = 0;
        if (this.gradingScaleExists()) {
            this.gradingScale()!.gradeSteps.forEach((gradeStep, i) => {
                if (this.gradingService.matchGradePercentage(gradeStep, percentage)) {
                    index = i;
                }
            });
        } else {
            index = Math.floor(percentage / this.binWidth);
            if (index >= 100 / this.binWidth) {
                // This happens, for 100%, if the exam total points were not set correctly or bonus points were given
                index = 100 / this.binWidth - 1;
            }
        }
        return index;
    }

    /**
     * Find the grade step index for the corresponding grade step by matchin grade names
     * @param gradeName the grade name corresponding to one of the grade names in the gradingScale.
     */
    findGradeStepIndexByGradeName(gradeName: string): number {
        // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
        const index = this.gradingScale()?.gradeSteps.findIndex((gradeStep) => gradeStep.gradeName === gradeName)!;
        return index >= 0 ? index : 0;
    }

    /**
     * Auxiliary method that increases the represented value of the bar covering the given score by 1
     * @param percentageOrGradeName the score or grade name that should be included in the distribution
     */
    private addToHistogram(percentageOrGradeName: number | string): void {
        // Update histogram data structure
        const histogramIndex = this.findGradeStepIndex(percentageOrGradeName);
        this.entries[histogramIndex].value++;
    }

    /**
     * Sets up the distribution chart for given scores
     */
    private createChart(): void {
        this.generateDefaultChartSetting();
        this.setupChartColoring();
        this.setupAxisLabels();
        const elements = this.scores() ?? this.gradeNames();
        if (!elements) {
            throw new Error('Either "scores" or "gradeNames" should be given as input.');
        }
        elements.forEach((scoreOrGradeName) => this.addToHistogram(scoreOrGradeName));
        this.chartEntries.set([...this.entries]);
    }

    /**
     * Determines and returns the maximum value displayed on the y-axis
     */
    private calculateTickMax(): number {
        const histogramData = this.entries.map((dataPack) => dataPack.value);
        const max = Math.max(...histogramData);
        return Math.ceil((max + 1) / 10) * 10 + 20;
    }

    /**
     * Determines and returns the translation path for the tooltip that is displayed when hovering over the help icon next to the legend
     */
    private determineHelpIconTooltip(): string {
        if (this.gradingScaleExists()) {
            if (this.isBonus()) {
                return 'artemisApp.examScores.gradingScaleExplanationBonus';
            } else {
                return this.getHelpIconTooltipNotBonus();
            }
        } else {
            return this.getHelpIconNoGradingScale();
        }
    }

    /**
     * Auxiliary method that returns the bar color of the grade step in the chart
     * @param gradeStep the grade step that should be colored
     * @returns the color that the given grade step should receive in the chart
     */
    private getGradeStepColor(gradeStep: GradeStep): string {
        if (this.isBonus()) {
            if (gradeStep.gradeName === '0') {
                return GraphColors.YELLOW;
            } else {
                return GraphColors.GREY;
            }
        } else {
            if (gradeStep.isPassingGrade) {
                return GraphColors.GREY;
            } else {
                return GraphColors.RED;
            }
        }
    }

    /**
     * Sets up the bar coloring
     * If no grading scale exists, all bars representing a score < 40% are colored yellow in order to visualize that this is a critical performance
     * If a grading scale exists that is bonus, all bars with a lower bound < 40% are colored yellow as well
     * If a grading scale exists that is not bonus, all bars representing a score that does not pass the exam are colored red
     * In either case, all bars above the thresholds remain grey
     */
    private setupChartColoring(): void {
        const domain: string[] = [];
        if (!this.gradingScaleExists()) {
            for (let i = 0; i < 100 / this.binWidth; i++) {
                if (i < 40 / this.binWidth) {
                    domain.push(GraphColors.YELLOW);
                } else {
                    domain.push(GraphColors.GREY);
                }
            }
        } else {
            this.gradingScale()!.gradeSteps.forEach((gradeStep) => {
                const color = this.getGradeStepColor(gradeStep);
                domain.push(color);
            });
        }
        this.backupDomain = domain;
        this.chartColors.set([...domain]);
    }

    /**
     * Auxiliary method in order to keep the chart translation sensitive
     */
    private setupAxisLabels(): void {
        this.yAxisLabel.set(this.translateService.instant('artemisApp.examScores.yAxes'));
        let xAxisLabel = this.translateService.instant('artemisApp.examScores.xAxes');

        if (this.gradingScaleExists() && !this.isBonus()) {
            xAxisLabel += this.translateService.instant('artemisApp.examScores.xAxesSuffixNoBonus');
        } else if (this.gradingScaleExists() && this.isBonus()) {
            xAxisLabel += this.translateService.instant('artemisApp.examScores.xAxesSuffixBonus');
        }
        this.xAxisLabel.set(xAxisLabel);
    }

    /**
     * Auxiliary method that returns the translation path for the tooltip if a grading scale exists that is not Bonus
     */
    private getHelpIconTooltipNotBonus(): string {
        if (this.isCourseScore()) {
            return 'artemisApp.instructorDashboard.courseScoreChart.gradingScaleExplanationNotBonus';
        } else {
            return 'artemisApp.examScores.gradingScaleExplanationNotBonus';
        }
    }

    /**
     * Auxiliary method that returns the translation path for the tooltip if no grading scale exists
     */
    private getHelpIconNoGradingScale(): string {
        if (this.isCourseScore()) {
            return 'artemisApp.instructorDashboard.courseScoreChart.noGradingScaleExplanation';
        } else {
            return 'artemisApp.examScores.noGradingScaleExplanation';
        }
    }

    /**
     * Highlights the score passed to the component by its parent component (if any is set).
     */
    private highlightScore(): void {
        if (this.scoreToHighlight() === undefined) {
            this.chartColors.set([...this.backupDomain]);
            return;
        }
        const index = this.findGradeStepIndex(this.scoreToHighlight()!);
        const bar = this.entries[index];

        if (bar.value > 0) {
            const domain = [...this.backupDomain];
            domain[index] = GraphColors.LIGHT_BLUE;
            this.chartColors.set(domain);
        }
    }

    /**
     * Re-emits chart bar clicks in the shape previously provided by ngx-charts.
     */
    onChartSelect(event: any): void {
        const selected = toChartSelectEvent(event, this.chartData());
        if (selected?.label !== undefined && selected.value !== undefined) {
            this.onSelect.emit({ name: selected.label, value: selected.value, label: selected.label });
        }
    }
}
