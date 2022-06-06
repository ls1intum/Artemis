import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { QuizStatisticUtil } from 'app/exercises/quiz/shared/quiz-statistic-util.service';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { Authority } from 'app/shared/constants/authority.constants';
import { QuizStatistics } from 'app/exercises/quiz/manage/statistics/quiz-statistics';
import { faSync } from '@fortawesome/free-solid-svg-icons';
import { calculateMaxScore } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistics.utils';
import { round } from 'app/shared/util/utils';

@Component({
    selector: 'jhi-quiz-statistic',
    templateUrl: './quiz-statistic.component.html',
    styleUrls: ['../quiz-point-statistic/quiz-point-statistic.component.scss', '../../../../../shared/chart/vertical-bar-chart.scss'],
})
export class QuizStatisticComponent extends QuizStatistics implements OnInit, OnDestroy {
    quizExercise: QuizExercise;
    private sub: Subscription;

    label: string[] = [];
    backgroundColor: string[] = [];
    ratedAverage: number;
    unratedAverage: number;

    maxScore: number;
    websocketChannelForData: string;

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
        private changeDetector: ChangeDetectorRef,
    ) {
        super(translateService);
        this.translateService.onLangChange.subscribe(() => {
            this.setAxisLabels('showStatistic.quizStatistic.xAxes', 'showStatistic.quizStatistic.yAxes');
            this.ngxData[this.ngxData.length - 1].name = this.translateService.instant('showStatistic.quizStatistic.average');
            this.ngxData = [...this.ngxData];
        });
    }

    ngOnInit() {
        this.sub = this.route.params.subscribe((params) => {
            // use different REST-call if the User is a Student
            if (this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA])) {
                this.quizExerciseService.find(params['exerciseId']).subscribe((res: HttpResponse<QuizExercise>) => {
                    this.loadQuizSuccess(res.body!);
                });
            }

            // subscribe websocket for new statistical data
            this.websocketChannelForData = '/topic/statistic/' + params['exerciseId'];
            this.jhiWebsocketService.subscribe(this.websocketChannelForData);

            // ask for new Data if the websocket for new statistical data was notified
            this.jhiWebsocketService.receive(this.websocketChannelForData).subscribe(() => {
                if (this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA])) {
                    this.quizExerciseService.find(params['exerciseId']).subscribe((res) => {
                        this.loadQuizSuccess(res.body!);
                    });
                }
            });
        });
        this.changeDetector.detectChanges();
    }

    ngOnDestroy() {
        this.jhiWebsocketService.unsubscribe(this.websocketChannelForData);
    }

    /**
     * This functions loads the Quiz, which is necessary to build the Web-Template
     * And it loads the new Data if the Websocket has been notified
     *
     * @param {QuizExercise} quiz: the quizExercise, which this quiz-statistic presents.
     */
    loadQuizSuccess(quiz: QuizExercise) {
        // if the Student finds a way to the Website -> the Student will be sent back to Courses
        if (!this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA])) {
            this.router.navigate(['/courses']);
        }
        this.quizExercise = quiz;
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
        this.ratedAverage = 0;
        this.unratedAverage = 0;

        // set data based on the CorrectCounters in the QuestionStatistics
        for (let i = 0; i < this.quizExercise.quizQuestions!.length; i++) {
            const question = this.quizExercise.quizQuestions![i];
            const statistic = question.quizQuestionStatistic!;
            const ratedCounter = statistic.ratedCorrectCounter!;
            const unratedCounter = statistic.unRatedCorrectCounter!;
            this.label.push(i + 1 + '.');
            this.backgroundColor.push('#5bc0de');
            this.ratedData.push(ratedCounter);
            this.unratedData.push(unratedCounter);
            this.ratedAverage = this.ratedAverage + ratedCounter * question.points!;
            this.unratedAverage = this.unratedAverage + unratedCounter * question.points!;
        }

        // set Background for invalid questions = grey
        for (let i = 0; i < this.quizExercise.quizQuestions!.length; i++) {
            if (this.quizExercise.quizQuestions![i].invalid) {
                this.backgroundColor[i] = '#949494';
            }
        }

        // add data for the last bar (Average)
        this.backgroundColor.push('#1e3368');
        /*
         * we do not use roundScoreSpecifiedByCourseSettings() here as it is not necessary to make the rounding of the average correct solutions
         * in a quiz dependent of the individual course settings
         */
        this.ratedData.push(round(this.ratedAverage / this.maxScore, 2));
        this.unratedData.push(round(this.unratedAverage / this.maxScore, 2));

        // add Text for last label based on the language
        const lastLabel = this.translateService.instant('showStatistic.quizStatistic.average');
        this.label.push(lastLabel);
        this.chartLabels = this.label;
        this.ngxColor.domain = this.backgroundColor;

        // load data into chart
        this.loadDataInDiagram();
    }

    /**
     * updates the chart by setting the data set, re-calculating the height and calling update on the chart view child
     */
    loadDataInDiagram(): void {
        this.setData(this.quizExercise.quizPointStatistic!);
        this.pushDataToNgxEntry(this.changeDetector);
        this.setAxisLabels('showStatistic.quizStatistic.xAxes', 'showStatistic.quizStatistic.yAxes');
    }
}
