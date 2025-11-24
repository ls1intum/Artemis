import { Component, inject } from '@angular/core';
import { QuizStatisticUtil } from 'app/quiz/shared/service/quiz-statistic-util.service';
import { ShortAnswerQuestionUtil } from 'app/quiz/shared/service/short-answer-question-util.service';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { ShortAnswerQuestionStatistic } from 'app/quiz/shared/entities/short-answer-question-statistic.model';
import { ShortAnswerSolution } from 'app/quiz/shared/entities/short-answer-solution.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuestionStatisticComponent, blueColor, greenColor } from 'app/quiz/manage/statistics/question-statistic.component';
import { faCheckCircle, faSync, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { BarChartModule } from '@swimlane/ngx-charts';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { QuizStatisticsFooterComponent } from '../quiz-statistics-footer/quiz-statistics-footer.component';

@Component({
    selector: 'jhi-short-answer-question-statistic',
    templateUrl: './short-answer-question-statistic.component.html',
    providers: [QuizStatisticUtil, ShortAnswerQuestionUtil],
    styleUrls: [
        '../../../../shared/chart/vertical-bar-chart.scss',
        '../quiz-point-statistic/quiz-point-statistic.component.scss',
        './short-answer-question-statistic.component.scss',
    ],
    imports: [TranslateDirective, BarChartModule, FaIconComponent, QuizStatisticsFooterComponent],
})
export class ShortAnswerQuestionStatisticComponent extends QuestionStatisticComponent {
    shortAnswerQuestionUtil = inject(ShortAnswerQuestionUtil);
    private artemisMarkdown = inject(ArtemisMarkdownService);

    declare question: ShortAnswerQuestion;

    textParts: string[][];
    lettersForSolutions: number[] = [];

    sampleSolutions: ShortAnswerSolution[] = [];

    // Icons
    faSync = faSync;
    faCheckCircle = faCheckCircle;
    faTimesCircle = faTimesCircle;

    loadQuiz(quiz: QuizExercise, refresh: boolean) {
        const updatedQuestion = super.loadQuizCommon(quiz);
        if (!updatedQuestion) {
            return;
        }

        // load Layout only at the opening (not if the websocket refreshed the data)
        if (!refresh) {
            this.questionTextRendered = this.artemisMarkdown.safeHtmlForMarkdown(this.question.text);
            this.generateShortAnswerStructure();
            this.generateLettersForSolutions();
            this.loadLayout();
        }
        this.loadData();
        this.sampleSolutions = this.shortAnswerQuestionUtil.getSampleSolutions(this.question);
    }

    generateShortAnswerStructure() {
        const textPartsData = this.shortAnswerQuestionUtil.divideQuestionTextIntoTextParts(this.question.text!);
        this.textParts = this.shortAnswerQuestionUtil.transformTextPartsIntoHTML(textPartsData);
    }

    generateLettersForSolutions() {
        for (const mapping of this.question.correctMappings || []) {
            for (const i in this.question.spots) {
                if (mapping.spot?.id === this.question.spots[+i].id) {
                    this.lettersForSolutions.push(+i);
                    break;
                }
            }
        }
    }

    getSampleSolutionForSpot(spotTag: string): ShortAnswerSolution {
        const index = this.question.spots!.findIndex((spot) => spot.spotNr === this.shortAnswerQuestionUtil.getSpotNr(spotTag));
        return this.sampleSolutions[index];
    }

    /**
     * build the Chart-Layout based on the Json-entity (questionStatistic)
     */
    loadLayout() {
        this.resetLabelsColors();

        // set label and background color based on the spots
        this.question.spots!.forEach((_spot, i) => {
            this.labels.push(this.getLetter(i) + '.');
            this.solutionLabels.push(this.getLetter(i) + '.');
            this.backgroundColors.push(blueColor);
            this.backgroundSolutionColors.push(greenColor);
        });

        this.addLastBarLayout(this.question.spots!.length);
        this.loadInvalidLayout(this.question.spots!);
    }

    /**
     * load the Data from the Json-entity to the chart: myChart
     */
    loadData() {
        this.resetData();

        // set data based on the spots for each spot
        this.question.spots!.forEach((spot) => {
            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
            const spotCounter = (this.questionStatistic as ShortAnswerQuestionStatistic).shortAnswerSpotCounters?.find((sCounter) => {
                return spot.id === sCounter.spot?.id;
            })!;
            this.addData(spotCounter.ratedCounter!, spotCounter.unRatedCounter!);
        });
        this.updateData();
    }
}
