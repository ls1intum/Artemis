import { Component, OnDestroy, OnInit, inject, input } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { QuizStatisticUtil } from 'app/quiz/shared/service/quiz-statistic-util.service';
import { ShortAnswerQuestionUtil } from 'app/quiz/shared/service/short-answer-question-util.service';
import { TranslateService } from '@ngx-translate/core';
import { HttpResponse } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { MultipleChoiceQuestionStatistic } from 'app/quiz/shared/entities/multiple-choice-question-statistic.model';
import { QuizPointStatistic } from 'app/quiz/shared/entities/quiz-point-statistic.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { Authority } from 'app/shared/constants/authority.constants';
import { UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { ArtemisServerDateService } from 'app/shared/service/server-date.service';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgClass } from '@angular/common';
import { JhiConnectionStatusComponent } from 'app/shared/connection-status/connection-status.component';
import { TruncatePipe } from 'app/shared/pipes/truncate.pipe';

@Component({
    selector: 'jhi-quiz-statistics-footer',
    templateUrl: './quiz-statistics-footer.component.html',
    providers: [QuizStatisticUtil, ShortAnswerQuestionUtil],
    styleUrls: ['./quiz-statistics-footer.component.scss', '../../../shared/quiz.scss'],
    imports: [NgbDropdown, NgbDropdownToggle, FaIconComponent, TranslateDirective, NgbDropdownMenu, RouterLink, NgClass, JhiConnectionStatusComponent, TruncatePipe],
})
export class QuizStatisticsFooterComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private accountService = inject(AccountService);
    private translateService = inject(TranslateService);
    private quizExerciseService = inject(QuizExerciseService);
    private quizStatisticUtil = inject(QuizStatisticUtil);
    private websocketService = inject(WebsocketService);
    private serverDateService = inject(ArtemisServerDateService);

    isQuizPointStatistic = input<boolean>();
    isQuizStatistic = input<boolean>();

    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;

    quizExercise: QuizExercise;
    question: QuizQuestion;
    quizPointStatistic: QuizPointStatistic;
    questionStatistic: MultipleChoiceQuestionStatistic;
    questionIdParam: number;
    private websocketChannelForData: string;
    // timer
    waitingForQuizStart = false;
    remainingTimeText = '?';
    remainingTimeSeconds = 0;
    interval: any;

    // Icons
    farListAlt = faListAlt;

    ngOnInit() {
        this.route.params.subscribe((params) => {
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
     * @param remainingTimeSeconds the amount of seconds to display
     * @return humanized text for the given amount of seconds
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
        this.websocketService.unsubscribe(this.websocketChannelForData);
    }

    /**
     * This functions loads the Quiz, which is necessary to build the Web-Template
     * And it loads the new Data if the Websocket has been notified
     *
     * @param quiz the quizExercise, which this quiz-statistic presents.
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

        if (this.isQuizStatistic()) {
            this.router.navigateByUrl(baseUrl + `/quiz-point-statistic`);
        } else if (this.isQuizPointStatistic()) {
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

        if (this.isQuizPointStatistic()) {
            this.router.navigateByUrl(baseUrl + `/quiz-statistic`);
        } else if (this.isQuizStatistic()) {
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
