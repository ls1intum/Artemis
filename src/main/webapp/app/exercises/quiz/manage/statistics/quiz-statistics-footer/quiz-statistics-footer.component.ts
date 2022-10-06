import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { QuizStatisticUtil } from 'app/exercises/quiz/shared/quiz-statistic-util.service';
import { ShortAnswerQuestionUtil } from 'app/exercises/quiz/shared/short-answer-question-util.service';
import { TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz/quiz-question.model';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { MultipleChoiceQuestionStatistic } from 'app/entities/quiz/multiple-choice-question-statistic.model';
import { QuizPointStatistic } from 'app/entities/quiz/quiz-point-statistic.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Authority } from 'app/shared/constants/authority.constants';
import { UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { ArtemisServerDateService } from 'app/shared/server-date.service';

@Component({
    selector: 'jhi-quiz-statistics-footer',
    templateUrl: './quiz-statistics-footer.component.html',
    providers: [QuizStatisticUtil, ShortAnswerQuestionUtil],
    styleUrls: ['./quiz-statistics-footer.component.scss', '../../../shared/quiz.scss'],
})
export class QuizStatisticsFooterComponent implements OnInit, OnDestroy {
    @Input() isQuizPointStatistic: boolean;
    @Input() isQuizStatistic: boolean;

    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;

    quizExercise: QuizExercise;
    question: QuizQuestion;
    quizPointStatistic: QuizPointStatistic;
    questionStatistic: MultipleChoiceQuestionStatistic;
    questionIdParam: number;
    private sub: Subscription;
    private websocketChannelForData: string;
    // timer
    waitingForQuizStart = false;
    remainingTimeText = '?';
    remainingTimeSeconds = 0;
    interval: any;

    // Icons
    farListAlt = faListAlt;

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private accountService: AccountService,
        private translateService: TranslateService,
        private quizExerciseService: QuizExerciseService,
        private quizStatisticUtil: QuizStatisticUtil,
        private jhiWebsocketService: JhiWebsocketService,
        private serverDateService: ArtemisServerDateService,
    ) {}

    ngOnInit() {
        this.sub = this.route.params.subscribe((params) => {
            this.questionIdParam = +params['questionId'];
            if (this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA])) {
                this.quizExerciseService.find(params['exerciseId']).subscribe((res: HttpResponse<QuizExercise>) => {
                    this.loadQuiz(res.body!);
                });
            }
        });

        // update displayed times in UI regularly
        this.interval = setInterval(() => {
            this.updateDisplayedTimes();
        }, UI_RELOAD_TIME);
    }

    /**
     * updates all displayed (relative) times in the UI
     */
    updateDisplayedTimes() {
        const translationBasePath = 'artemisApp.showStatistic.';
        // update remaining time
        if (this.quizExercise && this.quizExercise.dueDate) {
            const endDate = this.quizExercise.dueDate;
            if (endDate.isAfter(this.serverDateService.now())) {
                // quiz is still running => calculate remaining seconds and generate text based on that
                this.remainingTimeSeconds = endDate.diff(this.serverDateService.now(), 'seconds');
                this.remainingTimeText = this.relativeTimeText(this.remainingTimeSeconds);
            } else {
                // quiz is over => set remaining seconds to negative, to deactivate 'Submit' button
                this.remainingTimeSeconds = -1;
                this.remainingTimeText = this.translateService.instant(translationBasePath + 'quizHasEnded');
            }
        } else {
            // remaining time is unknown => Set remaining seconds to 0, to keep 'Submit' button enabled
            this.remainingTimeSeconds = 0;
            this.remainingTimeText = '?';
        }
    }

    /**
     * Express the given timespan as humanized text
     *
     * @param remainingTimeSeconds {number} the amount of seconds to display
     * @return {string} humanized text for the given amount of seconds
     */
    relativeTimeText(remainingTimeSeconds: number) {
        if (remainingTimeSeconds > 210) {
            return Math.ceil(remainingTimeSeconds / 60) + ' min';
        } else if (remainingTimeSeconds > 59) {
            return Math.floor(remainingTimeSeconds / 60) + ' min ' + (remainingTimeSeconds % 60) + ' s';
        } else {
            return remainingTimeSeconds + ' s';
        }
    }

    ngOnDestroy() {
        clearInterval(this.interval);
        this.jhiWebsocketService.unsubscribe(this.websocketChannelForData);
    }

    /**
     * This functions loads the Quiz, which is necessary to build the Web-Template
     * And it loads the new Data if the Websocket has been notified
     *
     * @param {QuizExercise} quiz: the quizExercise, which this quiz-statistic presents.
     */
    loadQuiz(quiz: QuizExercise) {
        // if the Student finds a way to the Website -> the Student will be sent back to Courses
        if (!this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA])) {
            this.router.navigate(['/courses']);
        }
        this.quizExercise = quiz;
        const updatedQuestion = this.quizExercise.quizQuestions?.filter((question) => this.questionIdParam === question.id)[0];
        this.question = updatedQuestion as QuizQuestion;
        this.waitingForQuizStart = !this.quizExercise.quizStarted;
    }

    /**
     * This function navigates to the previous quiz question statistic.
     * If the current page shows the first quiz question statistic then it will navigate to the quiz statistic
     */
    previousStatistic() {
        const baseUrl = this.quizStatisticUtil.getBaseUrlForQuizExercise(this.quizExercise);

        if (this.isQuizStatistic) {
            this.router.navigateByUrl(baseUrl + `/quiz-point-statistic`);
        } else if (this.isQuizPointStatistic) {
            if (!this.quizExercise.quizQuestions || this.quizExercise.quizQuestions.length === 0) {
                this.router.navigateByUrl(baseUrl + `/quiz-statistic`);
            } else {
                // go to previous question-statistic
                this.quizStatisticUtil.navigateToStatisticOf(this.quizExercise, this.quizExercise.quizQuestions.last()!);
            }
        } else {
            this.quizStatisticUtil.previousStatistic(this.quizExercise, this.question);
        }
    }

    /**
     * This function navigates to the next quiz question statistic.
     * If the current page shows the last quiz question statistic then it will navigate to the quiz point statistic
     */
    nextStatistic() {
        const baseUrl = this.quizStatisticUtil.getBaseUrlForQuizExercise(this.quizExercise);

        if (this.isQuizPointStatistic) {
            this.router.navigateByUrl(baseUrl + `/quiz-statistic`);
        } else if (this.isQuizStatistic) {
            // go to quiz-statistic if the position = last position
            if (!this.quizExercise.quizQuestions || this.quizExercise.quizQuestions.length === 0) {
                this.router.navigateByUrl(baseUrl + `/quiz-point-statistic`);
            } else {
                // go to next question-statistic
                this.quizStatisticUtil.navigateToStatisticOf(this.quizExercise, this.quizExercise.quizQuestions[0]);
            }
        } else {
            this.quizStatisticUtil.nextStatistic(this.quizExercise, this.question);
        }
    }
}
