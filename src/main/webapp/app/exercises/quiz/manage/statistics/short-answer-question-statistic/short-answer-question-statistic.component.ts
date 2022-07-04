import { ChangeDetectorRef, Component } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { QuizStatisticUtil } from 'app/exercises/quiz/shared/quiz-statistic-util.service';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { ShortAnswerQuestion } from 'app/entities/quiz/short-answer-question.model';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { ShortAnswerQuestionStatistic } from 'app/entities/quiz/short-answer-question-statistic.model';
import { ShortAnswerSolution } from 'app/entities/quiz/short-answer-solution.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { blueColor, greenColor, QuestionStatisticComponent } from 'app/exercises/quiz/manage/statistics/question-statistic.component';
import { faCheckCircle, faSync, faTimesCircle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-short-answer-question-statistic',
    templateUrl: './short-answer-question-statistic.component.html',
    providers: [QuizStatisticUtil, ShortAnswerQuestionUtil],
    styleUrls: [
        '../../../../../shared/chart/vertical-bar-chart.scss',
        '../quiz-point-statistic/quiz-point-statistic.component.scss',
        './short-answer-question-statistic.component.scss',
    ],
})
export class ShortAnswerQuestionStatisticComponent extends QuestionStatisticComponent {
    question: ShortAnswerQuestion;

    textParts: string[][];
    lettersForSolutions: number[] = [];

    sampleSolutions: ShortAnswerSolution[] = [];

    // Icons
    faSync = faSync;
    faCheckCircle = faCheckCircle;
    faTimesCircle = faTimesCircle;

    constructor(
        route: ActivatedRoute,
        router: Router,
        accountService: AccountService,
        translateService: TranslateService,
        quizExerciseService: QuizExerciseService,
        jhiWebsocketService: JhiWebsocketService,
        quizStatisticUtil: QuizStatisticUtil,
        public shortAnswerQuestionUtil: ShortAnswerQuestionUtil,
        private artemisMarkdown: ArtemisMarkdownService,
        protected changeDetector: ChangeDetectorRef,
    ) {
        super(route, router, accountService, translateService, quizExerciseService, jhiWebsocketService, changeDetector);
    }

    /**
     * This functions loads the Quiz, which is necessary to build the Web-Template
     *
     * @param {QuizExercise} quiz: the quizExercise, which the selected question is part of.
     * @param {boolean} refresh: true if method is called from Websocket
     */
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
        const textParts = this.shortAnswerQuestionUtil.divideQuestionTextIntoTextParts(this.question.text!);
        this.textParts = this.shortAnswerQuestionUtil.transformTextPartsIntoHTML(textParts);
    }

    generateLettersForSolutions() {
        for (const mapping of this.question.correctMappings || []) {
            for (const i in this.question.spots) {
                if (mapping.spot!.id === this.question.spots[i].id) {
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

        // set label and backgroundcolor based on the spots
        this.question.spots!.forEach((spot, i) => {
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
            const spotCounter = (this.questionStatistic as ShortAnswerQuestionStatistic).shortAnswerSpotCounters?.find((sCounter) => {
                return spot.id === sCounter.spot?.id;
            })!;
            this.addData(spotCounter.ratedCounter!, spotCounter.unRatedCounter!);
        });
        this.updateData();
    }
}
