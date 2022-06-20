import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { QuizStatisticUtil } from 'app/exercises/quiz/shared/quiz-statistic-util.service';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { PointCounter } from 'app/entities/quiz/point-counter.model';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { QuizPointStatistic } from 'app/entities/quiz/quiz-point-statistic.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Authority } from 'app/shared/constants/authority.constants';
import { blueColor } from 'app/exercises/quiz/manage/statistics/question-statistic.component';
import { UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';
import { round } from 'app/shared/util/utils';
import { QuizStatistics } from 'app/exercises/quiz/manage/statistics/quiz-statistics';
import { TranslateService } from '@ngx-translate/core';
import { faSync } from '@fortawesome/free-solid-svg-icons';
import { calculateMaxScore } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistics.utils';
import { ArtemisServerDateService } from 'app/shared/server-date.service';

@Component({
    selector: 'jhi-quiz-point-statistic',
    templateUrl: './quiz-point-statistic.component.html',
    styleUrls: ['./quiz-point-statistic.component.scss', '../../../../../shared/chart/vertical-bar-chart.scss'],
})
export class QuizPointStatisticComponent extends QuizStatistics implements OnInit, OnDestroy {
    readonly round = round;

    quizExercise: QuizExercise;
    quizPointStatistic: QuizPointStatistic;
    private sub: Subscription;

    labels: string[] = [];

    label: string[] = [];
    backgroundColor: string[] = [];

    maxScore: number;
    websocketChannelForData: string;
    quizExerciseChannel: string;

    // variables for ngx-charts
    legend = false;
    showXAxisLabel = true;
    showYAxisLabel = true;
    xAxis = true;
    yAxis = true;
    roundEdges = true;
    showDataLabel = true;
    height = 500;
    tooltipDisabled = true;
    animations = false;

    // timer
    waitingForQuizStart = false;
    remainingTimeText = '?';
    remainingTimeSeconds = 0;
    interval: any;

    // Icons
    faSync = faSync;

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private accountService: AccountService,
        protected translateService: TranslateService,
        private quizExerciseService: QuizExerciseService,
        private quizStatisticUtil: QuizStatisticUtil,
        private jhiWebsocketService: JhiWebsocketService,
        protected changeDetector: ChangeDetectorRef,
        private serverDateService: ArtemisServerDateService,
    ) {
        super(translateService);
        this.translateService.onLangChange.subscribe(() => {
            this.setAxisLabels('showStatistic.quizPointStatistic.xAxes', 'showStatistic.quizPointStatistic.yAxes');
        });
    }

    ngOnInit() {
        this.sub = this.route.params.subscribe((params) => {
            // use different REST-call if the User is a Student
            if (this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA])) {
                this.quizExerciseService.find(params['exerciseId']).subscribe((res) => {
                    this.loadQuizSuccess(res.body!);
                });
            }

            // subscribe websocket for new statistical data
            this.websocketChannelForData = '/topic/statistic/' + params['exerciseId'];
            this.jhiWebsocketService.subscribe(this.websocketChannelForData);

            if (!this.quizExerciseChannel) {
                this.quizExerciseChannel = '/topic/courses/' + params['courseId'] + '/quizExercises';

                // quizExercise channel => react to changes made to quizExercise (e.g. start date)
                this.jhiWebsocketService.subscribe(this.quizExerciseChannel);
                this.jhiWebsocketService.receive(this.quizExerciseChannel).subscribe((quiz) => {
                    if (this.waitingForQuizStart && params['exerciseId'] === quiz.id) {
                        this.loadQuizSuccess(quiz);
                    }
                });
            }

            // ask for new Data if the websocket for new statistical data was notified
            this.jhiWebsocketService.receive(this.websocketChannelForData).subscribe((quiz) => {
                this.loadNewData(quiz.quizPointStatistic);
            });
        });

        // update displayed times in UI regularly
        this.interval = setInterval(() => {
            this.updateDisplayedTimes();
        }, UI_RELOAD_TIME);
        this.changeDetector.detectChanges();
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
     * load the new quizPointStatistic from the server if the Websocket has been notified
     *
     * @param {QuizPointStatistic} statistic: the new quizPointStatistic
     *                                          from the server with the new Data.
     */
    loadNewData(statistic: QuizPointStatistic) {
        // if the Student finds a way to the Website
        //      -> the Student will be send back to Courses
        if (!this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA])) {
            this.router.navigate(['courses']);
        }
        this.quizPointStatistic = statistic;
        this.loadData();
    }

    /**
     * This functions loads the Quiz, which is necessary to build the Web-Template
     *
     * @param {QuizExercise} quizExercise: the quizExercise,
     *                              which the this quiz-point-statistic presents.
     */
    loadQuizSuccess(quizExercise: QuizExercise) {
        // if the Student finds a way to the Website
        //      -> the Student will be sent back to Courses
        if (!this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA])) {
            this.router.navigate(['courses']);
        }
        this.quizExercise = quizExercise;
        this.waitingForQuizStart = !this.quizExercise.quizStarted;
        this.quizPointStatistic = this.quizExercise.quizPointStatistic!;
        this.maxScore = calculateMaxScore(this.quizExercise);

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
            let label = '[' + Math.max(pointCounter.points! - 0.5, 0) + ' - ' + Math.min(pointCounter.points! + 0.5, this.maxScore);
            label += index !== this.quizPointStatistic.pointCounters!.length - 1 ? ')' : ']';
            this.label.push(label);
            this.ratedData.push(pointCounter.ratedCounter!);
            this.unratedData.push(pointCounter.unRatedCounter!);
            this.backgroundColor.push(blueColor);
        });

        this.chartLabels = this.label;
        this.ngxColor.domain = this.backgroundColor;

        // load data into the chart
        this.loadDataInDiagram();
    }

    /**
     * check if the rated or unrated
     * load the rated or unrated data into the diagram
     */
    loadDataInDiagram(): void {
        this.setData(this.quizPointStatistic);
        this.pushDataToNgxEntry(this.changeDetector);

        // add Axes-labels based on selected language
        this.setAxisLabels('artemisApp.showStatistic.quizPointStatistic.xAxes', 'artemisApp.showStatistic.quizPointStatistic.yAxes');
    }

    /**
     *
     * Recalculate the complete statistic on the server in case something went wrong with it
     *
     */
    recalculate() {
        this.quizExerciseService.recalculate(this.quizExercise.id!).subscribe((res) => {
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
