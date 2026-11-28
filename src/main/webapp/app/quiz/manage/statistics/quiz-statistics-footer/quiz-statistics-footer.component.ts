import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { TumAetUiMenuComponent, TumAetUiMenuItemDirective, TumAetUiMenuTriggerDirective } from '@tumaet/ui-angular/menu';
import { ConnectedPosition } from '@angular/cdk/overlay';
import { isExamExerciseRoute } from 'app/exercise/util/exercise-detail-route-context';
import { Component, OnDestroy, OnInit, computed, inject, input, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { QuizStatisticUtil } from 'app/quiz/shared/service/quiz-statistic-util.service';
import { ShortAnswerQuestionUtil } from 'app/quiz/shared/service/short-answer-question-util.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { UI_RELOAD_TIME } from 'app/foundation/constants/exercise-exam-constants';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { faChevronDown } from '@fortawesome/free-solid-svg-icons';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { NgClass } from '@angular/common';
import { JhiConnectionStatusComponent } from 'app/shared-ui/connection-status/connection-status.component';
import { TruncatePipe } from 'app/foundation/pipes/truncate.pipe';
import { quizCountdown } from 'app/quiz/shared/util/quiz-time.util';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-quiz-statistics-footer',
    templateUrl: './quiz-statistics-footer.component.html',
    providers: [QuizStatisticUtil, ShortAnswerQuestionUtil],
    styleUrls: ['./quiz-statistics-footer.component.css', '../../../shared/quiz.css'],
    imports: [
        TumAetUiButtonDirective,
        TumAetUiMenuComponent,
        TumAetUiMenuItemDirective,
        TumAetUiMenuTriggerDirective,
        FaIconComponent,
        TranslateDirective,
        RouterLink,
        NgClass,
        JhiConnectionStatusComponent,
        TruncatePipe,
        ArtemisTranslatePipe,
    ],
})
export class QuizStatisticsFooterComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private accountService = inject(AccountService);
    private translateService = inject(TranslateService);
    private quizExerciseService = inject(QuizExerciseService);
    private quizStatisticUtil = inject(QuizStatisticUtil);
    private serverDateService = inject(ArtemisServerDateService);

    isQuizPointStatistic = input<boolean>();
    isQuizStatistic = input<boolean>();

    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;

    readonly quizExercise = signal<QuizExercise>(undefined!);
    readonly question = signal<QuizQuestion>(undefined!);
    /** Root of the statistic routes of the quiz, which differ between course and exam quizzes. */
    readonly statisticBaseUrl = computed(() => (this.quizExercise() ? this.quizStatisticUtil.getBaseUrlForQuizExercise(this.quizExercise()) : undefined));
    /** The footer sits at the bottom of the viewport, so the menu opens above its trigger. */
    protected readonly menuAbovePositions: ConnectedPosition[] = [
        { originX: 'start', originY: 'top', overlayX: 'start', overlayY: 'bottom', offsetY: -4 },
        { originX: 'start', originY: 'bottom', overlayX: 'start', overlayY: 'top', offsetY: 4 },
    ];
    questionIdParam!: number; // set in ngOnInit() from the route params before loadQuiz() reads it
    // timer
    readonly waitingForQuizStart = signal(false);
    readonly remainingTimeText = signal('?');
    readonly remainingTimeSeconds = signal(0);
    interval!: ReturnType<typeof setInterval>; // set in ngOnInit() before ngOnDestroy() clears it

    // Icons
    farListAlt = faListAlt;
    faChevronDown = faChevronDown;

    /** The route segment of the statistic of the given question. */
    questionStatisticSegment(question: QuizQuestion): string | undefined {
        switch (question.type) {
            case QuizQuestionType.MULTIPLE_CHOICE:
                return 'mc-question-statistic';
            case QuizQuestionType.DRAG_AND_DROP:
                return 'dnd-question-statistic';
            case QuizQuestionType.SHORT_ANSWER:
                return 'sa-question-statistic';
            default:
                return undefined;
        }
    }

    ngOnInit() {
        this.route.params.subscribe((params) => {
            this.questionIdParam = +params['questionId'];
            if (this.accountService.isAtLeastTutor()) {
                (isExamExerciseRoute(this.route.snapshot)
                    ? this.quizExerciseService.findForExam(params['exerciseId'])
                    : this.quizExerciseService.find(params['exerciseId'])
                ).subscribe((quizExercise) => this.loadQuiz(quizExercise));
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
        const countdown = quizCountdown(this.quizExercise()?.dueDate, this.serverDateService.now(), this.translateService.instant('artemisApp.showStatistic.quizHasEnded'));
        this.remainingTimeSeconds.set(countdown.seconds);
        this.remainingTimeText.set(countdown.text);
    }

    ngOnDestroy() {
        clearInterval(this.interval);
    }

    /**
     * This functions loads the Quiz, which is necessary to build the Web-Template
     * And it loads the new Data if the Websocket has been notified
     *
     * @param quiz the quizExercise, which this quiz-statistic presents.
     */
    loadQuiz(quiz: QuizExercise) {
        // if the Student finds a way to the Website -> the Student will be sent back to Courses
        if (!this.accountService.isAtLeastTutor()) {
            void this.router.navigate(['/courses']);
        }
        this.quizExercise.set(quiz);
        const updatedQuestion = this.quizExercise().quizQuestions?.filter((question) => this.questionIdParam === question.id)[0];
        this.question.set(updatedQuestion as QuizQuestion);
        this.waitingForQuizStart.set(!this.quizExercise().quizStarted);
    }

    /**
     * This function navigates to the previous quiz question statistic.
     * If the current page shows the first quiz question statistic then it will navigate to the quiz statistic
     */
    previousStatistic() {
        const quizExercise = this.quizExercise();
        const baseUrl = this.quizStatisticUtil.getBaseUrlForQuizExercise(quizExercise);

        if (this.isQuizStatistic()) {
            void this.router.navigateByUrl(baseUrl + `/quiz-point-statistic`);
        } else if (this.isQuizPointStatistic()) {
            if (!quizExercise.quizQuestions || quizExercise.quizQuestions.length === 0) {
                void this.router.navigateByUrl(baseUrl + `/quiz-statistic`);
            } else {
                // go to previous question-statistic
                this.quizStatisticUtil.navigateToStatisticOf(quizExercise, quizExercise.quizQuestions.last()!);
            }
        } else {
            this.quizStatisticUtil.previousStatistic(quizExercise, this.question());
        }
    }

    /**
     * This function navigates to the next quiz question statistic.
     * If the current page shows the last quiz question statistic then it will navigate to the quiz point statistic
     */
    nextStatistic() {
        const quizExercise = this.quizExercise();
        const baseUrl = this.quizStatisticUtil.getBaseUrlForQuizExercise(quizExercise);

        if (this.isQuizPointStatistic()) {
            void this.router.navigateByUrl(baseUrl + `/quiz-statistic`);
        } else if (this.isQuizStatistic()) {
            // go to quiz-statistic if the position = last position
            if (!quizExercise.quizQuestions || quizExercise.quizQuestions.length === 0) {
                void this.router.navigateByUrl(baseUrl + `/quiz-point-statistic`);
            } else {
                // go to next question-statistic
                this.quizStatisticUtil.navigateToStatisticOf(quizExercise, quizExercise.quizQuestions[0]);
            }
        } else {
            this.quizStatisticUtil.nextStatistic(quizExercise, this.question());
        }
    }
}
