import { Component, OnDestroy, OnInit } from '@angular/core';
import { QuizExercise, QuizExerciseService } from '../../entities/quiz-exercise';
import { ActivatedRoute, Router } from '@angular/router';
import { JhiWebsocketService, Principal } from '../../shared';
import { TranslateService } from '@ngx-translate/core';

import * as Chart from 'chart.js';
import { QuizPointStatistic } from '../../entities/quiz-point-statistic';
import { QuizStatisticUtil } from '../../components/util/quiz-statistic-util.service';
import { QuestionType } from '../../entities/question';

interface DataSet {
    data: Array<number>;
    backgroundColor: Array<string>;
}

@Component({
    selector: 'jhi-quiz-point-statistic',
    templateUrl: './quiz-point-statistic.component.html',
    providers: [QuizStatisticUtil]
})
export class QuizPointStatisticComponent implements OnInit, OnDestroy {

    // make constants available to html for comparison
    readonly DRAG_AND_DROP = QuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuestionType.MULTIPLE_CHOICE;

    quizExercise: QuizExercise;
    quizPointStatistic: QuizPointStatistic;
    private sub: any;

    labels = new Array<string>();
    data = new Array<number>();
    colors = new Array<string>();
    chartType = 'bar';
    datasets = new Array<DataSet>();

    label = new Array<string>();
    ratedData = new Array<number>();
    unratedData = new Array<number>();
    backgroundColor = new Array<string>();

    maxScore: number;
    rated = true;
    participants: number;
    websocketChannelForData: string;
    websocketChannelForReleaseState: string;

    // options for chart in chart.js style
    options = {
        layout: {
            padding: {
                left: 0,
                right: 0,
                top: 0,
                bottom: 30
            }
        },
        legend: {
            display: false
        },
        title: {
            display: false,
            text: '',
            position: 'top',
            fontSize: '16',
            padding: 20
        },
        tooltips: {
            enabled: false
        },
        scales: {
            yAxes: [{
                scaleLabel: {
                    labelString: '',
                    display: true
                },
                ticks: {
                    beginAtZero: true
                }
            }],
            xAxes: [{
                scaleLabel: {
                    labelString: '',
                    display: true
                }
            }]
        },
        hover: {animationDuration: 0},
        // add numbers on top of the bars
        animation: {
            duration: 500,
            onComplete: (chartInstance: Chart) => {
                const ctx = chartInstance.ctx;
                const fontSize = 12;
                const fontStyle = 'normal';
                const fontFamily = 'Arial';
                ctx.font = Chart.helpers.fontString(fontSize, fontStyle, fontFamily);
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';

                this.datasets.forEach((dataset, i) => {
                    const meta = chartInstance.getDatasetMeta(i);
                    meta.data.forEach((bar: any, index) => {
                        const data = (Math.round(dataset.data[index] * 100) / 100).toString();
                        const dataPercentage = (Math.round((dataset.data[index] / this.participants) * 1000) / 10);

                        const position = bar.tooltipPosition();

                        // if the bar is high enough -> write the percentageValue inside the bar
                        if (dataPercentage > 6) {
                            // if the bar is low enough -> write the amountValue above the bar
                            if (position.y > 15) {
                                ctx.fillStyle = 'black';
                                ctx.fillText(data, position.x, position.y - 10);

                                if (this.participants !== 0) {
                                    ctx.fillStyle = 'white';
                                    ctx.fillText(dataPercentage.toString()
                                        + '%', position.x, position.y + 10);
                                }
                            } else {
                                // if the bar is too high -> write the amountValue inside the bar
                                ctx.fillStyle = 'white';
                                if (this.participants !== 0) {
                                    ctx.fillText(data + ' / ' + dataPercentage.toString()
                                        + '%', position.x, position.y + 10);
                                } else {
                                    ctx.fillText(data, position.x, position.y + 10);
                                }
                            }
                        } else {
                            // if the bar is to low -> write the percentageValue above the bar
                            ctx.fillStyle = 'black';
                            if (this.participants !== 0) {
                                ctx.fillText(data + ' / ' + dataPercentage.toString()
                                    + '%', position.x, position.y - 10);
                            } else {
                                ctx.fillText(data, position.x, position.y - 10);
                            }
                        }
                    });
                });
            }
        }
    };

    constructor(private route: ActivatedRoute,
                private router: Router,
                private principal: Principal,
                private translateService: TranslateService,
                private quizExerciseService: QuizExerciseService,
                private jhiWebsocketService: JhiWebsocketService,
                private quizStatisticUtil: QuizStatisticUtil) {}

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

    /**
     * load the new quizPointStatistic from the server if the Websocket has been notified
     *
     * @param {QuizPointStatistic} statistic: the new quizPointStatistic
     *                                          from the server with the new Data.
     */
    loadNewData(statistic: QuizPointStatistic) {
        // if the Student finds a way to the Website, while the Statistic is not released
        //      -> the Student will be send back to Courses
        if ((!this.principal.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'])) && !statistic.released) {
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
        if ((!this.principal.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'])) && quiz.quizPointStatistic.released === false) {
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
        this.quizPointStatistic.pointCounters.forEach(pointCounter => {
            this.label.push(pointCounter.points.toString());
            this.ratedData.push(pointCounter.ratedCounter);
            this.unratedData.push(pointCounter.unRatedCounter);
            this.backgroundColor.push('#428bca');
        });
        // order the bars ascending on points
        this.order();

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
        this.datasets = [{
            data: this.data,
            backgroundColor: this.colors
        }];
        console.log(this.datasets);
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
     * order the data and the associated Labels, so that they are ascending (BubbleSort)
     */
    order() {
        let old = new Array<string>();
        while (old.toString() !== this.label.toString()) {
            old = this.label.slice();
            for (let i = 0; i < this.label.length - 1; i++) {
                if (this.label[i] > this.label[i + 1]) {
                    // switch Labels
                    const tempLabel = this.label[i];
                    this.label[i] = this.label[i + 1];
                    this.label[i + 1] = tempLabel;
                    // switch rated Data
                    const tempRatedData = this.ratedData[i];
                    this.ratedData[i] = this.ratedData[i + 1];
                    this.ratedData[i + 1] = tempRatedData;
                    // switch unrated Data
                    const tempUnratedData = this.unratedData[i];
                    this.unratedData[i] = this.unratedData[i + 1];
                    this.unratedData[i + 1] = tempUnratedData;
                }
            }
        }
    }

    /**
     * got to the Template with the previous Statistic -> the last QuestionStatistic
     * if there is no QuestionStatistic -> go to QuizStatistic
     */
    previousStatistic() {
        if (this.quizExercise.questions === null
            || this.quizExercise.questions.length === 0) {
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
