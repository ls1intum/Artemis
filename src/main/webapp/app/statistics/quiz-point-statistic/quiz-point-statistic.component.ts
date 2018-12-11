import { Component, OnDestroy, OnInit } from '@angular/core';
import { QuizExercise, QuizExerciseService } from '../../entities/quiz-exercise';
import { ActivatedRoute, Router } from '@angular/router';
import { JhiWebsocketService, Principal } from '../../core';
import { TranslateService } from '@ngx-translate/core';
import { QuizPointStatistic } from '../../entities/quiz-point-statistic';
import { ChartOptions } from 'chart.js';
import { QuizStatisticUtil } from '../../components/util/quiz-statistic-util.service';
import { QuestionType } from '../../entities/question';
import { createOptions, DataSet, DataSetProvider } from '../quiz-statistic/quiz-statistic.component';
import { Subscription } from 'rxjs/Subscription';
import { PointCounter } from 'app/entities/point-counter';

@Component({
    selector: 'jhi-quiz-point-statistic',
    templateUrl: './quiz-point-statistic.component.html',
    providers: [QuizStatisticUtil]
})
export class QuizPointStatisticComponent implements OnInit, OnDestroy, DataSetProvider {
    // make constants available to html for comparison
    readonly DRAG_AND_DROP = QuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuestionType.MULTIPLE_CHOICE;

    quizExercise: QuizExercise;
    quizPointStatistic: QuizPointStatistic;
    private sub: Subscription;

    labels: string[] = [];
    data: number[] = [];
    colors: string[] = [];
    chartType = 'bar';
    datasets: DataSet[] = [];

    label: string[] = [];
    ratedData: number[] = [];
    unratedData: number[] = [];
    backgroundColor: string[] = [];

    maxScore: number;
    rated = true;
    participants: number;
    websocketChannelForData: string;
    websocketChannelForReleaseState: string;

    // options for chart.js style
    options: ChartOptions;

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private principal: Principal,
        private translateService: TranslateService,
        private quizExerciseService: QuizExerciseService,
        private jhiWebsocketService: JhiWebsocketService,
        private quizStatisticUtil: QuizStatisticUtil
    ) {
        this.options = createOptions(this);
    }

    ngOnInit() {
        this.sub = this.route.params.subscribe(params => {
            // use different REST-call if the User is a Student
            if (this.principal.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'])) {
                this.quizExerciseService.find(params['quizId']).subscribe(res => {
                    this.loadQuizSuccess(res.body);
                });
            } else {
                this.quizExerciseService.findForStudent(params['quizId']).subscribe(res => {
                    this.loadQuizSuccess(res.body);
                });
            }

            // subscribe websocket for new statistical data
            this.websocketChannelForData = '/topic/statistic/' + params['quizId'];
            this.jhiWebsocketService.subscribe(this.websocketChannelForData);

            // subscribe websocket which notifies the user if the release status was changed
            this.websocketChannelForReleaseState = this.websocketChannelForData + '/release';
            this.jhiWebsocketService.subscribe(this.websocketChannelForReleaseState);

            // ask for new Data if the websocket for new statistical data was notified
            this.jhiWebsocketService.receive(this.websocketChannelForData).subscribe(quiz => {
                this.loadNewData(quiz.quizPointStatistic);
            });
            // refresh release information
            this.jhiWebsocketService.receive(this.websocketChannelForReleaseState).subscribe(payload => {
                this.quizExercise.quizPointStatistic.released = payload;
                // send students back to courses if the statistic was revoked
                if (!this.principal.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA']) && !payload) {
                    this.router.navigate(['/courses']);
                }
            });

            // add Axes-labels based on selected language
            this.translateService.get('showStatistic.quizPointStatistic.xAxes').subscribe(xLabel => {
                this.options.scales.xAxes[0].scaleLabel.labelString = xLabel;
            });
            this.translateService.get('showStatistic.quizPointStatistic.yAxes').subscribe(yLabel => {
                this.options.scales.yAxes[0].scaleLabel.labelString = yLabel;
            });
        });
    }

    ngOnDestroy() {
        this.jhiWebsocketService.unsubscribe(this.websocketChannelForData);
        this.jhiWebsocketService.unsubscribe(this.websocketChannelForReleaseState);
    }

    getDataSets() {
        return this.datasets;
    }

    getParticipants() {
        return this.participants;
    }

    /**
     * load the new quizPointStatistic from the server if the Websocket has been notified
     *
     * @param {QuizPointStatistic} statistic: the new quizPointStatistic
     *                                          from the server with the new Data.
     */
    loadNewData(statistic: QuizPointStatistic) {
        // if the Student finds a way to the Website, while the Statistic is not released
        //      -> the Student will be send back to Courses
        if (!this.principal.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA']) && !statistic.released) {
            this.router.navigate(['courses']);
        }
        this.quizPointStatistic = statistic;
        this.loadData();
    }

    /**
     * This functions loads the Quiz, which is necessary to build the Web-Template
     *
     * @param {QuizExercise} quiz: the quizExercise,
     *                              which the this quiz-point-statistic presents.
     */
    loadQuizSuccess(quiz: QuizExercise) {
        // if the Student finds a way to the Website, while the Statistic is not released
        //      -> the Student will be send back to Courses
        if (
            !this.principal.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA']) &&
            quiz.quizPointStatistic.released === false
        ) {
            this.router.navigate(['courses']);
        }
        this.quizExercise = quiz;
        this.quizPointStatistic = this.quizExercise.quizPointStatistic;
        this.maxScore = this.calculateMaxScore();

        this.loadData();
    }

    /**
     * calculate the maximal  possible Score for the quiz
     *
     * @return (number): sum over the Scores of all questions
     */
    calculateMaxScore() {
        let result = 0;

        this.quizExercise.questions.forEach(function(question) {
            result = result + question.score;
        });
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
        // set data based on the pointCounters
        this.order(this.quizPointStatistic.pointCounters).forEach(pointCounter => {
            this.label.push(pointCounter.points.toString());
            this.ratedData.push(pointCounter.ratedCounter);
            this.unratedData.push(pointCounter.unRatedCounter);
            this.backgroundColor.push('#428bca');
        });

        this.labels = this.label;
        this.colors = this.backgroundColor;

        // load data into the chart
        this.loadDataInDiagram();
    }

    /**
     * check if the rated or unrated
     * load the rated or unrated data into the diagram
     */
    loadDataInDiagram() {
        if (this.rated) {
            this.participants = this.quizPointStatistic.participantsRated;
            this.data = this.ratedData;
        } else {
            // load the unrated data
            this.participants = this.quizPointStatistic.participantsUnrated;
            this.data = this.unratedData;
        }
        this.datasets = [
            {
                data: this.data,
                backgroundColor: this.colors
            }
        ];
    }

    /**
     *
     * Recalculate the complete statistic on the server in case something went wrong with it
     *
     */
    recalculate() {
        this.quizExerciseService.recalculate(this.quizExercise.id).subscribe(res => {
            this.loadQuizSuccess(res.body);
        });
    }

    /**
     * switch between showing and hiding the solution in the chart
     *  1. change the amount of  participants
     *  2. change the bar-Data
     */
    switchRated() {
        this.rated = !this.rated;
        this.loadDataInDiagram();
    }

    /**
     * order the point cursors ascending
     */
    order(pointCursors: Array<PointCounter>) {
        return pointCursors.sort((a: PointCounter, b: PointCounter) => {
            if (a.points < b.points) {
                return -1;
            }
            if (a.points > b.points) {
                return 1;
            }
            // a must be equal to b
            return 0;
        });
    }

    /**
     * got to the Template with the previous Statistic -> the last QuestionStatistic
     * if there is no QuestionStatistic -> go to QuizStatistic
     */
    previousStatistic() {
        if (this.quizExercise.questions === null || this.quizExercise.questions.length === 0) {
            this.router.navigateByUrl('/quiz/' + this.quizExercise.id + '/quiz-statistic');
        } else {
            const previousQuestion = this.quizExercise.questions[this.quizExercise.questions.length - 1];
            if (previousQuestion.type === QuestionType.MULTIPLE_CHOICE) {
                this.router.navigateByUrl('/quiz/' + this.quizExercise.id + '/multiple-choice-question-statistic/' + previousQuestion.id);
            } else if (previousQuestion.type === QuestionType.DRAG_AND_DROP) {
                this.router.navigateByUrl('/quiz/' + this.quizExercise.id + 'drag-and-drop-question-statistic/' + previousQuestion.id);
            }
        }
    }

    /**
     * release of revoke all statistics of the quizExercise
     *
     * @param {boolean} released: true to release, false to revoke
     */
    releaseStatistics(released: boolean) {
        if (released) {
            this.quizExerciseService.releaseStatistics(this.quizExercise.id);
        } else {
            this.quizExerciseService.revokeStatistics(this.quizExercise.id);
        }
    }

    /**
     * check if it's allowed to release the Statistic (allowed if the quiz is finished)
     * @returns {boolean} true if it's allowed, false if not
     */
    releaseButtonDisabled() {
        this.quizStatisticUtil.releaseButtonDisabled(this.quizExercise);
    }
}
