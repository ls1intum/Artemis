import { Directive } from '@angular/core';
import { Color, ScaleType } from '@swimlane/ngx-charts';
import { QuizQuestionStatistic } from 'app/entities/quiz/quiz-question-statistic.model';
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
    // questionStatistic: QuizQuestionStatistic;

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

    protected pushDataToNgxEntry(): void {
        this.ngxData = [];
        this.data.forEach((score, index) => {
            this.ngxData.push({ name: this.chartLabels[index], value: score });
        });
        this.maxScale = calculateHeightOfChartData(this.data);
        this.ngxData = [...this.ngxData];
    }

    protected formatDataLabel(value: any) {
        const relativeValue = (value / this.totalParticipants) * 100;
        if (isNaN(relativeValue)) {
            return value + ' (0%)';
        } else {
            return value + ' (' + round((value / this.participants) * 100, 1) + '%)';
        }
    }
}
