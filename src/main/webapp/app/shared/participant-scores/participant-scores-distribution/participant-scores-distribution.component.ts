import { Component, Input, OnInit } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { GradeType, GradingScale } from 'app/entities/grading-scale.model';
import { GradingSystemService } from 'app/grading-system/grading-system.service';
import { TranslateService } from '@ngx-translate/core';
import { GradeStep } from 'app/entities/grade-step.model';
import { GraphColors } from 'app/entities/statistics.model';

@Component({
    selector: 'jhi-participant-scores-distribution',
    templateUrl: './participant-scores-distribution.component.html',
    styleUrls: ['./participant-score-distribution.component.scss', '../../chart/vertical-bar-chart.scss'],
})
export class ParticipantScoresDistributionComponent implements OnInit {
    @Input()
    scores: number[];

    @Input()
    gradingScale?: GradingScale;

    @Input()
    isCourseScore = true;
    gradingScaleExists = false;
    isBonus?: boolean;
    ngxData: NgxChartsSingleSeriesDataEntry[];
    histogramData: number[];
    yScaleMax: number;
    height = 500;

    yAxisLabel = this.translateService.instant('artemisApp.examScores.yAxes');
    xAxisLabel = this.translateService.instant('artemisApp.examScores.xAxes');

    gradingKeyTooltip: string;

    readonly binWidth = 5;
    ngxColor = {
        name: 'Participation scores distribution',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    } as Color;

    constructor(private gradingSystemService: GradingSystemService, private translateService: TranslateService) {}

    ngOnInit() {
        this.translateService.onLangChange.subscribe(() => {
            this.setupAxisLabels();
        });
        if (this.gradingScale) {
            this.gradingScaleExists = true;
            this.isBonus = this.gradingScale.gradeType === GradeType.BONUS;

            if (!this.isCourseScore) {
                this.height = 400;
            }
        }
        this.createChart(this.scores);
        this.yScaleMax = this.calculateTickMax();
        this.determineHelpIconTooltip();
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
            this.histogramData = Array(this.gradingScale!.gradeSteps.length);
            this.ngxData = [];
            for (let i = 0; i < this.gradingScale!.gradeSteps.length; i++) {
                this.ngxData.push({ name: i.toString(), value: 0 });
            }
        } else {
            this.histogramData = Array(100 / this.binWidth);
            for (let i = 0; i < 100 / this.binWidth; i++) {
                this.ngxData.push({ name: i.toString(), value: 0 });
            }
        }

        this.histogramData.fill(0);
        this.createChartLabels();
    }

    private createChartLabels() {
        if (this.gradingScaleExists) {
            this.gradingScale!.gradeSteps.forEach((gradeStep, i) => {
                let label = gradeStep.lowerBoundInclusive || i === 0 ? '[' : '(';
                label += `${gradeStep.lowerBoundPercentage},${gradeStep.upperBoundPercentage}`;
                label += gradeStep.upperBoundInclusive || i === 100 ? ']' : ')';
                label += ` {${gradeStep.gradeName}}`;
                this.ngxData[i].name = label;
            });
        } else {
            for (let i = 0; i < this.histogramData.length; i++) {
                let label = `[${i * this.binWidth},${(i + 1) * this.binWidth}`;
                label += i === this.histogramData.length - 1 ? ']' : ')';

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

    addToHistogram(percentage: number) {
        // Update histogram data structure
        const histogramIndex = this.findGradeStepIndex(percentage);
        this.ngxData[histogramIndex].value++;
        this.histogramData[histogramIndex]++;
    }

    createChart(scores: number[]) {
        this.generateDefaultNgxChartsSetting();
        this.setupChartColoring();
        this.setupAxisLabels();

        scores.forEach((score) => this.addToHistogram(score));
    }

    private calculateTickMax() {
        const max = Math.max(...this.histogramData);
        return Math.ceil((max + 1) / 10) * 10 + 20;
    }

    private determineHelpIconTooltip() {
        if (this.gradingScaleExists) {
            if (this.isBonus) {
                this.gradingKeyTooltip = 'artemisApp.examScores.gradingScaleExplanationBonus';
            } else if (this.isCourseScore) {
                this.gradingKeyTooltip = 'instructorDashboard.courseScoreChart.gradingScaleExplanationNotBonus';
            } else {
                this.gradingKeyTooltip = 'artemisApp.examScores.gradingScaleExplanationNotBonus';
            }
        } else if (this.isCourseScore) {
            this.gradingKeyTooltip = 'instructorDashboard.courseScoreChart.noGradingScaleExplanation';
        } else {
            this.gradingKeyTooltip = 'artemisApp.examScores.noGradingScaleExplanation';
        }
    }

    /**
     * Auxiliary method that returns the bar color of the grade step in the chart
     * @param gradeStep the grade step that should be colored
     * @returns string representation of the color
     * @private
     */
    private getGradeStepColor(gradeStep: GradeStep): GraphColors {
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
}
