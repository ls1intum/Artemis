import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AbstractQuizStatisticComponent } from 'app/quiz/manage/statistics/quiz-statistics';
import { AccountService } from 'app/core/auth/account.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { PointCounter } from 'app/quiz/shared/entities/point-counter.model';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { QuizPointStatistic } from 'app/quiz/shared/entities/quiz-point-statistic.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { blueColor } from 'app/quiz/manage/statistics/question-statistic.component';
import { UI_RELOAD_TIME } from 'app/foundation/constants/exercise-exam-constants';
import { round } from 'app/foundation/util/utils';
import { faSync } from '@fortawesome/free-solid-svg-icons';
import { calculateMaxScore } from 'app/quiz/manage/statistics/quiz-statistic/quiz-statistics.utils';
import { ArtemisServerDateService } from 'app/foundation/service/server-date.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ChartModule } from 'primeng/chart';
import { QuizStatisticsFooterComponent } from '../quiz-statistics-footer/quiz-statistics-footer.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Subscription } from 'rxjs';
import { formatQuizRelativeTime } from 'app/quiz/shared/util/quiz-time.util';

@Component({
    selector: 'jhi-quiz-point-statistic',
    templateUrl: './quiz-point-statistic.component.html',
    styleUrls: ['./quiz-point-statistic.component.scss'],
    imports: [FaIconComponent, TranslateDirective, ChartModule, QuizStatisticsFooterComponent, ArtemisTranslatePipe],
})
export class QuizPointStatisticComponent extends AbstractQuizStatisticComponent implements OnInit, OnDestroy {
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private accountService = inject(AccountService);
    private quizExerciseService = inject(QuizExerciseService);
    private websocketService = inject(WebsocketService);
    private serverDateService = inject(ArtemisServerDateService);

    readonly round = round;

    readonly quizExercise = signal<QuizExercise>(undefined!);
    quizPointStatistic: QuizPointStatistic;

    labels: string[] = [];

    label: string[] = [];
    backgroundColor: string[] = [];

    readonly maxScore = signal<number>(undefined!);
    websocketChannelForData: string;
    quizExerciseChannel: string;
    private quizExerciseSubscription?: Subscription;
    private quizDataSubscription?: Subscription;

    // timer
    waitingForQuizStart = false;
    readonly remainingTimeText = signal('?');
    readonly remainingTimeSeconds = signal(0);
    interval: ReturnType<typeof setInterval>;

    // Icons
    faSync = faSync;

    ngOnInit() {
        this.translateService.onLangChange.subscribe(() => {
            this.setAxisLabels('showStatistic.quizPointStatistic.xAxes', 'showStatistic.quizPointStatistic.yAxes');
        });
        this.route.params.subscribe((params) => {
            // use different REST-call if the User is a Student
            if (this.accountService.isAtLeastTutor()) {
                this.quizExerciseService.find(params['exerciseId']).subscribe((res) => {
                    this.loadQuizSuccess(res.body!);
                });
            }

            // subscribe websocket for new statistical data
            this.websocketChannelForData = '/topic/statistic/' + params['exerciseId'];

            if (!this.quizExerciseChannel) {
                this.quizExerciseChannel = '/topic/courses/' + params['courseId'] + '/quizExercises';

                // quizExercise channel => react to changes made to quizExercise (e.g. start date)
                this.quizExerciseSubscription = this.websocketService.subscribe<QuizExercise>(this.quizExerciseChannel).subscribe((quiz: QuizExercise) => {
                    if (this.waitingForQuizStart && params['exerciseId'] === quiz.id) {
                        this.loadQuizSuccess(quiz);
                    }
                });
            }

            // ask for new Data if the websocket for new statistical data was notified
            this.quizDataSubscription = this.websocketService.subscribe<QuizExercise>(this.websocketChannelForData).subscribe((quiz: QuizExercise) => {
                if (quiz.quizPointStatistic) {
                    this.loadNewData(quiz.quizPointStatistic);
                }
            });
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
        if (this.quizExercise() && this.quizExercise().dueDate) {
            const endDate = this.quizExercise().dueDate!;
            if (endDate.isAfter(this.serverDateService.now())) {
                // quiz is still running => calculate remaining seconds and generate text based on that
                this.remainingTimeSeconds.set(endDate.diff(this.serverDateService.now(), 'seconds'));
                this.remainingTimeText.set(this.relativeTimeText(this.remainingTimeSeconds()));
            } else {
                // quiz is over => set remaining seconds to negative, to deactivate 'Submit' button
                this.remainingTimeSeconds.set(-1);
                this.remainingTimeText.set(this.translateService.instant(translationBasePath + 'quizHasEnded'));
            }
        } else {
            // remaining time is unknown => Set remaining seconds to 0, to keep 'Submit' button enabled
            this.remainingTimeSeconds.set(0);
            this.remainingTimeText.set('?');
        }
    }

    /**
     * Express the given timespan as humanized text
     *
     * @param remainingTimeSeconds the amount of seconds to display
     * @return humanized text for the given amount of seconds
     */
    relativeTimeText(remainingTimeSeconds: number) {
        return formatQuizRelativeTime(remainingTimeSeconds);
    }

    ngOnDestroy() {
        clearInterval(this.interval);
        this.quizExerciseSubscription?.unsubscribe();
        this.quizDataSubscription?.unsubscribe();
    }

    /**
     * load the new quizPointStatistic from the server if the Websocket has been notified
     *
     * @param statistic the new quizPointStatistic from the server with the new Data.
     */
    loadNewData(statistic: QuizPointStatistic) {
        // if the Student finds a way to the Website
        //      -> the Student will be sent back to Courses
        if (!this.accountService.isAtLeastTutor()) {
            this.router.navigate(['courses']);
        }
        this.quizPointStatistic = statistic;
        this.loadData();
    }

    /**
     * This functions loads the Quiz, which is necessary to build the Web-Template
     *
     * @param quizExercise the quizExercise, which this quiz-point-statistic presents.
     */
    loadQuizSuccess(quizExercise: QuizExercise) {
        // if the Student finds a way to the Website
        //      -> the Student will be sent back to Courses
        if (!this.accountService.isAtLeastTutor()) {
            this.router.navigate(['courses']);
        }
        this.quizExercise.set(quizExercise);
        this.waitingForQuizStart = !this.quizExercise().quizStarted;
        this.quizPointStatistic = this.quizExercise().quizPointStatistic!;
        this.maxScore.set(calculateMaxScore(this.quizExercise()));

        this.loadData();
    }

    /**
     * load the Data from the Json-entity to the chart: myChart
     */
    loadData() {
        // reset old data
        this.label = [];
        this.backgroundColor = [];
        this.ratedData = [];
        this.unratedData = [];
        // set data based on the pointCounters
        this.order(this.quizPointStatistic.pointCounters!).forEach((pointCounter, index) => {
            /*
            The label represents the value range covered by the corresponding bar.
            As we round the individual student scores to integers for the statistic,
            each bar covers the range from integer - 0.5 to integer + 0.5, the lower border is always included.
            Ex.: integer 2: chart bar summarizes all values between [1.5 - 2.5)
            We additionally have to make sure that the range is limited by the maximum and minimum reachable points in the quiz
            (no negative points are achievable and the maximum points are defined by the quiz itself)
            Lastly, the last bar in the chart also covers the maximum points, that is why we change the upper border notation in this case from ')' to ']'
             */
            let label = '[' + Math.max(pointCounter.points! - 0.5, 0) + ' - ' + Math.min(pointCounter.points! + 0.5, this.maxScore());
            label += index !== this.quizPointStatistic.pointCounters!.length - 1 ? ')' : ']';
            this.label.push(label);
            this.ratedData.push(pointCounter.ratedCounter!);
            this.unratedData.push(pointCounter.unRatedCounter!);
            this.backgroundColor.push(blueColor);
        });

        this.chartLabels = this.label;
        this.chartColors.set([...this.backgroundColor]);

        // load data into the chart
        this.loadDataInDiagram();
    }

    /**
     * check if the rated or unrated
     * load the rated or unrated data into the diagram
     */
    loadDataInDiagram(): void {
        this.setData(this.quizPointStatistic);
        this.updateChartData();

        // add Axes-labels based on selected language
        this.setAxisLabels('artemisApp.showStatistic.quizPointStatistic.xAxes', 'artemisApp.showStatistic.quizPointStatistic.yAxes');
    }

    /**
     *
     * Recalculate the complete statistic on the server in case something went wrong with it
     *
     */
    recalculate() {
        this.quizExerciseService.recalculate(this.quizExercise().id!).subscribe((res) => {
            this.loadQuizSuccess(res.body!);
        });
    }

    /**
     * order the point cursors ascending
     */
    order(pointCursors: Array<PointCounter>) {
        // TODO: use sorting service
        return pointCursors.sort((a: PointCounter, b: PointCounter) => {
            if (a.points! < b.points!) {
                return -1;
            }
            if (a.points! > b.points!) {
                return 1;
            }
            // a must be equal to b
            return 0;
        });
    }
}
