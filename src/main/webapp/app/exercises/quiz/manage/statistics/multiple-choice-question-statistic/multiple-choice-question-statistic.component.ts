import { Component, OnDestroy, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { QuizStatisticUtil } from 'app/exercises/quiz/shared/quiz-statistic-util.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { calculateTickMax } from '../quiz-statistic/quiz-statistic.component';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { MultipleChoiceQuestionStatistic } from 'app/entities/quiz/multiple-choice-question-statistic.model';
import { MultipleChoiceQuestion } from 'app/entities/quiz/multiple-choice-question.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuestionStatisticComponent } from 'app/exercises/quiz/manage/statistics/drag-and-drop-question-statistic/drag-and-drop-question-statistic.component';

@Component({
    selector: 'jhi-multiple-choice-question-statistic',
    templateUrl: './multiple-choice-question-statistic.component.html',
    providers: [QuizStatisticUtil, ArtemisMarkdownService],
})
export class MultipleChoiceQuestionStatisticComponent extends QuestionStatisticComponent implements OnInit, OnDestroy {
    questionStatistic: MultipleChoiceQuestionStatistic;
    question: MultipleChoiceQuestion;

    solutionLabels: string[] = [];

    answerTextRendered: SafeHtml[];

    constructor(
        route: ActivatedRoute,
        router: Router,
        accountService: AccountService,
        translateService: TranslateService,
        quizExerciseService: QuizExerciseService,
        jhiWebsocketService: JhiWebsocketService,
        private quizStatisticUtil: QuizStatisticUtil,
        private artemisMarkdown: ArtemisMarkdownService,
    ) {
        super(route, router, accountService, translateService, quizExerciseService, jhiWebsocketService);
    }

    ngOnInit() {
        super.init();
    }

    ngOnDestroy() {
        super.destroy();
    }

    /**
     * This functions loads the Quiz, which is necessary to build the Web-Template
     *
     * @param {QuizExercise} quiz: the quizExercise, which the selected question is part of.
     * @param {boolean} refresh: true if method is called from Websocket
     */
    loadQuiz(quiz: QuizExercise, refresh: boolean) {
        const updatedQuestion = super.loadQuizCommon(quiz);
        this.question = updatedQuestion as MultipleChoiceQuestion;
        this.questionStatistic = this.question.quizQuestionStatistic as MultipleChoiceQuestionStatistic;

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
        // reset old data
        this.labels = [];
        this.backgroundColors = [];
        const answerOptions = this.question.answerOptions!;
        this.backgroundSolutionColors = new Array(answerOptions.length + 1);
        this.solutionLabels = new Array(answerOptions.length + 1);

        // set label and background-Color based on the AnswerOptions
        answerOptions.forEach((answerOption, i) => {
            this.labels.push(String.fromCharCode(65 + i) + '.');
            this.backgroundColors.push(this.getBackgroundColor('#428bca'));
        });
        this.addLastBarLayout();
        this.loadInvalidLayout();
        this.loadSolutionLayout();
    }

    /**
     * add Layout for the last bar
     */
    addLastBarLayout() {
        // set backgroundColor for last bar
        this.backgroundColors.push(this.getBackgroundColor('#5bc0de'));
        const answerOptionsLength = this.question.answerOptions!.length;
        this.backgroundSolutionColors[answerOptionsLength] = this.getBackgroundColor('#5bc0de');

        // add Text for last label based on the language
        this.translateService.get('showStatistic.quizStatistic.yAxes').subscribe((lastLabel) => {
            this.solutionLabels[answerOptionsLength] = lastLabel.split(' ');
            this.labels[answerOptionsLength] = lastLabel.split(' ');
            this.chartLabels.length = 0;
            for (let i = 0; i < this.labels.length; i++) {
                this.chartLabels.push(this.labels[i]);
            }
        });
    }

    /**
     * change label and Color if a dropLocation is invalid
     */
    loadInvalidLayout() {
        // set Background for invalid answers = grey
        this.translateService.get('showStatistic.invalid').subscribe((invalidLabel) => {
            this.question.answerOptions!.forEach((answerOption, i) => {
                if (answerOption.invalid) {
                    this.backgroundColors[i] = this.getBackgroundColor('#838383');
                    this.backgroundSolutionColors[i] = this.getBackgroundColor('#838383');

                    this.solutionLabels[i] = String.fromCharCode(65 + i) + '. ' + invalidLabel;
                }
            });
        });
    }

    /**
     * load Layout for showSolution
     */
    loadSolutionLayout() {
        // add correct-text to the label based on the language
        this.translateService.get('showStatistic.questionStatistic.correct').subscribe((correctLabel) => {
            this.question.answerOptions!.forEach((answerOption, i) => {
                if (answerOption.isCorrect) {
                    // check if the answer is valid and if true:
                    //      change solution-label and -color
                    if (!answerOption.invalid) {
                        this.backgroundSolutionColors[i] = this.getBackgroundColor('#5cb85c');
                        this.solutionLabels[i] = String.fromCharCode(65 + i) + '. (' + correctLabel + ')';
                    }
                }
            });
        });

        // add incorrect-text to the label based on the language
        this.translateService.get('showStatistic.questionStatistic.incorrect').subscribe((incorrectLabel) => {
            this.question.answerOptions!.forEach((answerOption, i) => {
                if (!answerOption.isCorrect) {
                    // check if the answer is valid and if false:
                    //      change solution-label and -color
                    if (!answerOption.invalid) {
                        this.backgroundSolutionColors[i] = this.getBackgroundColor('#d9534f');
                        this.solutionLabels[i] = String.fromCharCode(65 + i) + '. (' + incorrectLabel + ')';
                    }
                }
            });
        });
    }

    /**
     * load the Data from the Json-entity to the chart: myChart
     */
    loadData() {
        // reset old data
        this.ratedData = [];
        this.unratedData = [];

        // set data based on the answerCounters for each AnswerOption
        this.question.answerOptions!.forEach((answerOption) => {
            const answerOptionCounter = this.questionStatistic.answerCounters!.filter((answerCounter) => answerOption.id === answerCounter.answer!.id)[0];
            this.ratedData.push(answerOptionCounter.ratedCounter!);
            this.unratedData.push(answerOptionCounter.unRatedCounter!);
        });
        // add data for the last bar (correct Solutions)
        this.ratedCorrectData = this.questionStatistic.ratedCorrectCounter!;
        this.unratedCorrectData = this.questionStatistic.unRatedCorrectCounter!;

        this.loadDataInDiagram();
    }

    loadDataInDiagram() {
        super.loadDataInDiagram();
        if (this.showSolution) {
            // show Solution
            this.chartLabels = this.solutionLabels;
        } else {
            // don't show Solution
            this.chartLabels = this.labels;
        }
    }
}
