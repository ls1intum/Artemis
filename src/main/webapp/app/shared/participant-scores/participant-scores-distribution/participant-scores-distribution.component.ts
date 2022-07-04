import { Component, EventEmitter, HostListener, Input, OnChanges, OnInit, Output } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { GradeType, GradingScale } from 'app/entities/grading-scale.model';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { TranslateService } from '@ngx-translate/core';
import { GradeStep } from 'app/entities/grade-step.model';
import { GraphColors } from 'app/entities/statistics.model';

interface NgxClickEvent {
    name: string;
    value: number;
    label: string;
}

@Component({
    selector: 'jhi-participant-scores-distribution',
    templateUrl: './participant-scores-distribution.component.html',
    styleUrls: ['./participant-score-distribution.component.scss', '../../chart/vertical-bar-chart.scss'],
})
export class ParticipantScoresDistributionComponent implements OnInit, OnChanges {
    @Input()
    scores: number[];

    @Input()
    gradingScale?: GradingScale;

    @Input()
    isCourseScore = true;

    @Input()
    dataLabelFormatting?: (submissionCount: number) => string;

    @Input()
    scoreToHighlight?: number;

    @Output()
    onSelect = new EventEmitter<NgxClickEvent>();

    gradingScaleExists = false;
    isBonus?: boolean;
    ngxData: NgxChartsSingleSeriesDataEntry[];
    yScaleMax: number;
    height = 500;

    showYAxisLabel = true;
    yAxisLabel = this.translateService.instant('artemisApp.examScores.yAxes');
    xAxisLabel = this.translateService.instant('artemisApp.examScores.xAxes');

    helpIconTooltip: string;

    readonly binWidth = 5;

    ngxColor = {
        name: 'Participation scores distribution',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    } as Color;
    backupDomain: string[];

    constructor(private gradingSystemService: GradingSystemService, private translateService: TranslateService) {}

    ngOnInit() {
        this.translateService.onLangChange.subscribe(() => {
            this.setupAxisLabels();
        });
        this.realignChart();
    }

    ngOnChanges() {
        if (this.gradingScale) {
            this.gradingScaleExists = true;
            this.isBonus = this.gradingScale.gradeType === GradeType.BONUS;
        }
        if (!this.isCourseScore) {
            // This is the old height for the exam statistics. We would like to keep it, but for course scores we increased it by 100px
            this.height = 400;
        }
        this.createChart();
        this.yScaleMax = this.calculateTickMax();
        this.helpIconTooltip = this.determineHelpIconTooltip();
        this.highlightScore();
    }

    /**
     * Handles the appearance adaption for smaller screens.
     * If the screen width is below 700px, the y axis label consumes too much space.
     * In this case, the corresponding configuration flag changes so that ngx-charts hides the label.
     */
    @HostListener('window:resize')
    realignChart() {
        if (window.innerWidth < 700 && this.showYAxisLabel) {
            this.showYAxisLabel = false;
        } else if (window.innerWidth > 700 && !this.showYAxisLabel) {
            this.showYAxisLabel = true;
        }
    }

    /**
     * fill ngxData with a default configuration. The assignment of the names is only a placeholder,
     * they will be set to default labels in createChart.
     * If a grading key exists, ngxData gets reset according to it in calculateFilterDependentStatistics.
     * If no grading key exists, this default configuration is presented to the user.
     * @private
     */
    private generateDefaultNgxChartsSetting(): void {
        this.ngxData = [];
        if (this.gradingScaleExists) {
            this.ngxData = [];
            this.gradingScale!.gradeSteps.forEach((step) => {
                this.ngxData.push({
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
                this.ngxData.push(entry);
            }
        }
        this.createChartLabels();
    }

    /**
     * Creates the chart labels displaying the intervals each bar covers depending on the existence and state of a grading key
     * @private
     */
    private createChartLabels(): void {
        if (this.gradingScaleExists) {
            this.gradingScale!.gradeSteps.forEach((gradeStep, i) => {
                let label = gradeStep.lowerBoundInclusive || i === 0 ? '[' : '(';
                label += `${gradeStep.lowerBoundPercentage},${gradeStep.upperBoundPercentage}`;
                label += gradeStep.upperBoundInclusive || i === 100 ? ']' : ')';
                label += ` {${gradeStep.gradeName}}`;
                this.ngxData[i].name = label;
            });
        } else {
            for (let i = 0; i < this.ngxData.length; i++) {
                let label = `[${i * this.binWidth},${(i + 1) * this.binWidth}`;
                label += i === this.ngxData.length - 1 ? ']' : ')';

                this.ngxData[i].name = label;
            }
        }

        this.ngxData = [...this.ngxData];
    }

    /**
     * Find the grade step index for the corresponding grade step to the given percentage
     * @param percentage the percentage which will be mapped to a grade step.
     */
    findGradeStepIndex(percentage: number): number {
        let index = 0;
        if (this.gradingScaleExists) {
            this.gradingScale!.gradeSteps.forEach((gradeStep, i) => {
                if (this.gradingSystemService.matchGradePercentage(gradeStep, percentage)) {
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
     * Auxiliary method that increases the represented value of the bar covering the given score by 1
     * @param percentage the score that should be included in the distribution
     * @private
     */
    private addToHistogram(percentage: number): void {
        // Update histogram data structure
        const histogramIndex = this.findGradeStepIndex(percentage);
        this.ngxData[histogramIndex].value++;
    }

    /**
     * Sets up the distribution chart for given scores
     * @private
     */
    private createChart(): void {
        this.generateDefaultNgxChartsSetting();
        this.setupChartColoring();
        this.setupAxisLabels();
        this.scores.forEach((score) => this.addToHistogram(score));
    }

    /**
     * Determines and returns the maximum value displayed on the y-axis
     * @private
     */
    private calculateTickMax(): number {
        const histogramData = this.ngxData.map((dataPack) => dataPack.value);
        const max = Math.max(...histogramData);
        return Math.ceil((max + 1) / 10) * 10 + 20;
    }

    /**
     * Determines and returns the translation path for the tooltip that is displayed when hovering over the help icon next to the legend
     * @private
     */
    private determineHelpIconTooltip(): string {
        if (this.gradingScaleExists) {
            if (this.isBonus) {
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
     * @private
     */
    private getGradeStepColor(gradeStep: GradeStep): string {
        if (this.isBonus) {
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
     * @private
     */
    private setupChartColoring(): void {
        this.ngxColor.domain = [];
        if (!this.gradingScaleExists) {
            for (let i = 0; i < 100 / this.binWidth; i++) {
                if (i < 40 / this.binWidth) {
                    this.ngxColor.domain.push(GraphColors.YELLOW);
                } else {
                    this.ngxColor.domain.push(GraphColors.GREY);
                }
            }
        } else {
            this.gradingScale!.gradeSteps.forEach((gradeStep) => {
                const color = this.getGradeStepColor(gradeStep);
                this.ngxColor.domain.push(color);
            });
        }
        this.backupDomain = this.ngxColor.domain;
        this.ngxData = [...this.ngxData];
    }

    /**
     * Auxiliary method in order to keep the chart translation sensitive
     * @private
     */
    private setupAxisLabels(): void {
        this.yAxisLabel = this.translateService.instant('artemisApp.examScores.yAxes');
        this.xAxisLabel = this.translateService.instant('artemisApp.examScores.xAxes');

        if (this.gradingScaleExists && !this.isBonus) {
            this.xAxisLabel += this.translateService.instant('artemisApp.examScores.xAxesSuffixNoBonus');
        } else if (this.gradingScaleExists && this.isBonus) {
            this.xAxisLabel += this.translateService.instant('artemisApp.examScores.xAxesSuffixBonus');
        }
    }

    /**
     * Auxiliary method that returns the translation path for the tooltip if a grading scale exists that is not Bonus
     * @private
     */
    private getHelpIconTooltipNotBonus(): string {
        if (this.isCourseScore) {
            return 'instructorDashboard.courseScoreChart.gradingScaleExplanationNotBonus';
        } else {
            return 'artemisApp.examScores.gradingScaleExplanationNotBonus';
        }
    }

    /**
     * Auxiliary method that returns the translation path for the tooltip if no grading scale exists
     * @private
     */
    private getHelpIconNoGradingScale(): string {
        if (this.isCourseScore) {
            return 'instructorDashboard.courseScoreChart.noGradingScaleExplanation';
        } else {
            return 'artemisApp.examScores.noGradingScaleExplanation';
        }
    }

    /**
     * Highlights the score passed to the component by its parent component (if any is set).
     * @private
     */
    private highlightScore(): void {
        if (this.scoreToHighlight === undefined) {
            this.ngxColor.domain = this.backupDomain;
            this.ngxData = [...this.ngxData];
            return;
        }
        const index = this.findGradeStepIndex(this.scoreToHighlight);
        const bar = this.ngxData[index];

        if (bar.value > 0) {
            this.ngxColor.domain[index] = GraphColors.LIGHT_BLUE;
            this.ngxData = [...this.ngxData];
        }
    }
}
