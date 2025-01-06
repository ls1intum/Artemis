import { Component, inject } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { QuizStatisticUtil } from 'app/exercises/quiz/shared/quiz-statistic-util.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { MultipleChoiceQuestionStatistic } from 'app/entities/quiz/multiple-choice-question-statistic.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuestionStatisticComponent, blueColor, greenColor, redColor } from 'app/exercises/quiz/manage/statistics/question-statistic.component';
import { faCheckCircle, faSync, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { BarChartModule } from '@swimlane/ngx-charts';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { QuizStatisticsFooterComponent } from '../quiz-statistics-footer/quiz-statistics-footer.component';

@Component({
    selector: 'jhi-multiple-choice-question-statistic',
    templateUrl: './multiple-choice-question-statistic.component.html',
    styleUrls: ['../quiz-point-statistic/quiz-point-statistic.component.scss', '../../../../../shared/chart/vertical-bar-chart.scss'],
    providers: [QuizStatisticUtil],
    imports: [TranslateDirective, BarChartModule, FaIconComponent, QuizStatisticsFooterComponent],
})
export class MultipleChoiceQuestionStatisticComponent extends QuestionStatisticComponent {
    private artemisMarkdown = inject(ArtemisMarkdownService);
    declare question: MultipleChoiceQuestion;

    answerTextRendered: SafeHtml[];

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
            // render Markdown-text
            this.questionTextRendered = this.artemisMarkdown.safeHtmlForMarkdown(this.question.text);
            this.answerTextRendered = this.question.answerOptions!.map((answer) => this.artemisMarkdown.safeHtmlForMarkdown(answer.text));
            this.loadLayout();
        }
        this.loadData();
    }

    /**
     * build the Chart-Layout based on the the Json-entity (questionStatistic)
     */
    loadLayout() {
        this.resetLabelsColors();
        const answerOptions = this.question.answerOptions!;

        // set label and background-Color based on the AnswerOptions
        answerOptions.forEach((_answerOption, i) => {
            this.labels.push(this.getLetter(i) + '.');
            this.backgroundColors.push(blueColor);
        });
        this.addLastBarLayout(this.question.answerOptions!.length);
        this.loadInvalidLayout(this.question.answerOptions!);
        this.loadSolutionLayout();
    }

    /**
     * load Layout for showSolution
     */
    loadSolutionLayout() {
        // add correct-text to the label based on the language
        const correctLabel = this.translateService.instant('artemisApp.showStatistic.questionStatistic.correct');
        const incorrectLabel = this.translateService.instant('artemisApp.showStatistic.questionStatistic.incorrect');
        this.question.answerOptions!.forEach((answerOption, i) => {
            if (answerOption.isCorrect === true) {
                // check if the answer is valid and if true: change solution-label and -color
                if (!answerOption.invalid) {
                    this.backgroundSolutionColors[i] = greenColor;
                    this.solutionLabels[i] = this.getLetter(i) + '. (' + correctLabel + ')';
                }
            }
            if (answerOption.isCorrect === false) {
                // check if the answer is valid and if false: change solution-label and -color
                if (!answerOption.invalid) {
                    this.backgroundSolutionColors[i] = redColor;
                    this.solutionLabels[i] = this.getLetter(i) + '. (' + incorrectLabel + ')';
                }
            }
        });
    }

    /**
     * load the Data from the Json-entity to the chart: myChart
     */
    loadData() {
        this.resetData();

        // set data based on the answerCounters for each AnswerOption
        this.question.answerOptions!.forEach((answerOption) => {
            const answerOptionCounter = (this.questionStatistic as MultipleChoiceQuestionStatistic).answerCounters!.filter(
                (answerCounter) => answerOption.id === answerCounter.answer!.id,
            )[0];
            this.addData(answerOptionCounter.ratedCounter!, answerOptionCounter.unRatedCounter!);
        });
        this.updateData();
    }
}
