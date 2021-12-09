import { Component, OnDestroy, OnInit } from '@angular/core';
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
import { QuizStatisticsDirective } from 'app/exercises/quiz/manage/statistics/quiz-statistics.directive';
import { faSync } from '@fortawesome/free-solid-svg-icons';

/**
 * this interface is adapted from chart.js
 */
export interface DataSet {
    data: number[];
    backgroundColor: string[];
}

export function calculateHeightOfChartData(data: number[]) {
    const max = Math.max(...data);
    // we provide 300 as buffer at the top to display labels
    const height = Math.ceil((max + 1) / 10) * 10;
    if (height < 10) {
        return height + 3;
    } else if (height < 1000) {
        // add 25%, round to the next 10
        return Math.ceil(height * 0.125) * 10;
    } else {
        // add 25%, round to the next 100
        return Math.ceil(height * 0.0125) * 100;
    }
}

@Component({
    selector: 'jhi-quiz-statistic',
    templateUrl: './quiz-statistic.component.html',
    styleUrls: ['../quiz-point-statistic/quiz-point-statistic.component.scss'],
})
export class QuizStatisticComponent extends QuizStatisticsDirective implements OnInit, OnDestroy {
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
        private translateService: TranslateService,
        private quizExerciseService: QuizExerciseService,
        private quizStatisticUtil: QuizStatisticUtil,
        private jhiWebsocketService: JhiWebsocketService,
    ) {
        super();
    }

    loadDataInDiagram(): void {
        throw new Error('Method not implemented.');
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
    }

    ngOnDestroy() {
        this.jhiWebsocketService.unsubscribe(this.websocketChannelForData);
    }

    /**
     * This functions loads the Quiz, which is necessary to build the Web-Template
     * And it loads the new Data if the Websocket has been notified
     *
     * @param {QuizExercise} quiz: the quizExercise, which the this quiz-statistic presents.
     */
    loadQuizSuccess(quiz: QuizExercise) {
        // if the Student finds a way to the Website -> the Student will be send back to Courses
        if (!this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA])) {
            this.router.navigate(['/courses']);
        }
        this.quizExercise = quiz;
        this.maxScore = this.calculateMaxScore();
        this.loadData();
    }

    /**
     * calculate the maximal  possible Score for the quiz
     *
     * @return (int): sum over the Scores of all questions
     */
    calculateMaxScore() {
        let result = 0;

        if (this.quizExercise.quizQuestions) {
            this.quizExercise.quizQuestions.forEach(function (question) {
                result = result + question.points!;
            });
        } else {
            result = this.quizExercise.maxPoints!;
        }
        return result;
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
        this.ratedData.push(this.ratedAverage / this.maxScore);
        this.unratedData.push(this.unratedAverage / this.maxScore);

        // add Text for last label based on the language
        const lastLabel = this.translateService.instant('showStatistic.quizStatistic.average');
        this.label.push(lastLabel);
        this.chartLabels = this.label;
        this.setData(this.quizExercise.quizPointStatistic!);

        // load data into chart
        this.updateChart();
    }

    /**
     * switch between showing and hiding the solution in the chart
     *  1. change the amount of  participants
     *  2. change the bar-Data
     */
    switchRated() {
        if (this.rated) {
            // load unrated Data
            this.data = this.unratedData;
            this.participants = this.quizExercise.quizPointStatistic!.participantsUnrated!;
            this.rated = false;
        } else {
            // load rated Data
            this.data = this.ratedData;
            this.participants = this.quizExercise.quizPointStatistic!.participantsRated!;
            this.rated = true;
        }

        this.updateChart();
    }

    /**
     * updates the chart by setting the data set, re-calculating the height and calling update on the chart view child
     */
    updateChart() {
        this.pushDataToNgxEntry();
        this.ngxColor.domain = this.backgroundColor;
        this.xAxisLabel = this.translateService.instant('showStatistic.quizStatistic.xAxes');
        this.yAxisLabel = this.translateService.instant('showStatistic.quizStatistic.yAxes');
    }
}
