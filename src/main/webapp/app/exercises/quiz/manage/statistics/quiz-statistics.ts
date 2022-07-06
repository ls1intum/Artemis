import { ChangeDetectorRef } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { round } from 'app/shared/util/utils';
import { QuizStatistic } from 'app/entities/quiz/quiz-statistic.model';
import { TranslateService } from '@ngx-translate/core';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';

export abstract class QuizStatistics {
    data: number[] = [];
    ratedData: number[] = [];
    unratedData: number[] = [];
    rated = true;
    participants: number;

    ngxData: NgxChartsSingleSeriesDataEntry[] = [];
    ngxColor = {
        name: 'quiz statistics',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    } as Color;
    bindFormatting = this.formatDataLabel.bind(this);
    xAxisLabel: string;
    yAxisLabel: string;
    maxScale: number;
    chartLabels: string[] = [];
    totalParticipants = 0;

    constructor(protected translateService: TranslateService) {}

    /**
     * Depending on if the rated or unrated results should be displayed,
     * The amount of participants as well as the corresponding scores are set
     * @param statistic the statistic containing amount of participation
     * @protected
     */
    protected setData(statistic: QuizStatistic): void {
        if (this.rated) {
            this.participants = statistic.participantsRated!;
            // if rated is true use the rated Data and add the rated CorrectCounter
            this.data = [];
            this.data = [...this.ratedData];
        } else {
            this.participants = statistic.participantsUnrated!;
            // if rated is false use the unrated Data and add the unrated CorrectCounter
            this.data = [];
            this.data = [...this.unratedData];
        }
    }

    /**
     * Creates dedicated objects of type NgxChartsSingleSeriesDataEntry that can be processed by ngx-charts
     * in order to visualize the scores and calculates the maximum value on the y-axis
     * in order to ensure a shapely display.
     * @protected
     */
    protected pushDataToNgxEntry(changeDetector: ChangeDetectorRef): void {
        this.ngxData = [];
        this.data.forEach((score, index) => {
            this.ngxData.push({ name: this.chartLabels[index], value: score });
        });
        this.maxScale = this.calculateHeightOfChartData(this.data);
        this.ngxData = [...this.ngxData];
        changeDetector.detectChanges();
    }

    /**
     * switch between showing and hiding the solution in the chart
     *  1. change the amount of  participants
     *  2. change the bar-Data
     */
    switchRated(): void {
        this.rated = !this.rated;
        this.loadDataInDiagram();
    }

    /**
     * Modifies the datalabel for each bar to the following pattern:
     * absolute value (absolute value/amount of participants)
     * @param absoluteValue the absolute value represented by the corresponding bar
     * @returns string of the following pattern: absolute value (relative value)
     * @protected
     */
    protected formatDataLabel(absoluteValue: number): string {
        const relativeValue = (absoluteValue / this.totalParticipants) * 100;
        if (isNaN(relativeValue)) {
            return absoluteValue + ' (0%)';
        } else {
            return absoluteValue + ' (' + round((absoluteValue / this.participants) * 100, 1) + '%)';
        }
    }

    /**
     * Calculates the maximum value on the y-axis on a chart depending on the data to display
     * @param data the array of data that is to display by the chart
     * @returns height of the chart
     * @private
     */
    private calculateHeightOfChartData(data: number[]): number {
        const max = Math.max(...data);
        // we provide 300 as buffer at the top to display labels
        const height = Math.ceil((max + 1) / 10) * 10;
        if (height < 10) {
            return height + 3;
        } else if (height < 1000) {
            // add 25%, round to the next 10
            return Math.ceil(height * 0.125) * 10;
        } else {
            // add 25%, round to the next 100
            return Math.ceil(height * 0.0125) * 100;
        }
    }

    /**
     * Sets the axis labels given the translation paths
     * @param xAxisLabel translation path for x-axis label
     * @param yAxisLabel translation path for y-axis label
     */
    setAxisLabels(xAxisLabel: string, yAxisLabel: string): void {
        this.xAxisLabel = this.translateService.instant(xAxisLabel);
        this.yAxisLabel = this.translateService.instant(yAxisLabel);
    }

    /**
     * check if the rated or unrated
     * load the rated or unrated data into the diagram
     */
    abstract loadDataInDiagram(): void;
}
