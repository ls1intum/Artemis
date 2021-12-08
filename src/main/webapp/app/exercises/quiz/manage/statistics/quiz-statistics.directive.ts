import { Directive } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { calculateHeightOfChartData } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';
import { round } from 'app/shared/util/utils';
import { NgxDataEntry } from 'app/entities/course.model';
import { QuizStatistic } from 'app/entities/quiz/quiz-statistic.model';

@Directive()
export abstract class QuizStatisticsDirective {
    data: number[] = [];
    ratedData: number[] = [];
    unratedData: number[] = [];
    rated = true;
    participants: number;

    ngxData: NgxDataEntry[] = [];
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
            this.data = [...this.ratedData];
        } else {
            this.participants = statistic.participantsUnrated!;
            // if rated is false use the unrated Data and add the unrated CorrectCounter
            this.data = [...this.unratedData];
        }
    }

    /**
     * Creates dedicated objects of type NgxDataEntry that can be processed by ngx-charts
     * in order to visualize the scores and calculates the maximum value on the y axis
     * in order to ensure a shapely display.
     * @protected
     */
    protected pushDataToNgxEntry(): void {
        this.ngxData = [];
        this.data.forEach((score, index) => {
            this.ngxData.push({ name: this.chartLabels[index], value: score });
        });
        this.maxScale = calculateHeightOfChartData(this.data);
        this.ngxData = [...this.ngxData];
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
}
