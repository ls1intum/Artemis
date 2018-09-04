import { Component, OnDestroy, OnInit } from '@angular/core';
import { QuizExercise, QuizExerciseService } from '../../entities/quiz-exercise';
import { ActivatedRoute, Router } from '@angular/router';
import { JhiWebsocketService, Principal } from '../../shared';
import { TranslateService } from '@ngx-translate/core';
import { HttpResponse } from '@angular/common/http';

import { Chart } from 'chart.js';
import { QuizStatisticUtil } from '../../components/util/quiz-statistic-util.service';
import { QuestionType } from '../../entities/question';


interface DataSet {
    data: Array<number>;
    backgroundColor: Array<string>;
}

@Component({
    selector: 'jhi-quiz-statistic',
    templateUrl: './quiz-statistic.component.html'
})
export class QuizStatisticComponent implements OnInit, OnDestroy {

    // make constants available to html for comparison
    readonly DRAG_AND_DROP = QuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuestionType.MULTIPLE_CHOICE;

    quizExercise: QuizExercise;
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
    ratedAverage: number;
    unratedAverage: number;

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
                                    ctx.fillText(dataPercentage.toString() + '%', position.x, position.y + 10);
                                }
                            } else {
                                // if the bar is too high -> write the amountValue inside the bar
                                ctx.fillStyle = 'white';
                                if (this.participants !== 0) {
                                    ctx.fillText(data + ' / ' + dataPercentage.toString() + '%', position.x, position.y + 10);
                                } else {
                                    ctx.fillText(data, position.x, position.y + 10);
                                }
                            }
                        } else {
                            // if the bar is to low -> write the percentageValue above the bar
                            ctx.fillStyle = 'black';
                            if (this.participants !== 0) {
                                ctx.fillText(data + ' / ' + dataPercentage.toString() + '%', position.x, position.y - 10);
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
                this.quizExerciseService.find(params['quizId']).subscribe((res: HttpResponse<QuizExercise>) => {
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
            this.jhiWebsocketService.receive(this.websocketChannelForData).subscribe(() => {
                if (this.principal.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'])) {
                    this.quizExerciseService.find(params['quizId']).subscribe(res => {
                        this.loadQuizSuccess(res.body);
                    });
                } else {
                    this.quizExerciseService.findForStudent(params['quizId']).subscribe(res => {
                        this.loadQuizSuccess(res.body);
                    });
                }
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
            this.translateService.get('showStatistic.quizStatistic.xAxes').subscribe(xLabel => {
                this.options.scales.xAxes[0].scaleLabel.labelString = xLabel;
            });
            this.translateService.get('showStatistic.quizStatistic.yAxes').subscribe(yLabel => {
                this.options.scales.yAxes[0].scaleLabel.labelString = yLabel;
            });
        });
    }

    ngOnDestroy() {
        this.jhiWebsocketService.unsubscribe(this.websocketChannelForData);
        this.jhiWebsocketService.unsubscribe(this.websocketChannelForReleaseState);
    }

    /**
     * This functions loads the Quiz, which is necessary to build the Web-Template
     * And it loads the new Data if the Websocket has been notified
     *
     * @param {QuizExercise} quiz: the quizExercise, which the this quiz-statistic presents.
     */
    loadQuizSuccess(quiz: QuizExercise) {
        // if the Student finds a way to the Website, while the Statistic is not released -> the Student will be send back to Courses
        if ((!this.principal.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'])) && quiz.quizPointStatistic.released === false) {
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
        this.ratedAverage = 0;
        this.unratedAverage = 0;

        // set data based on the CorrectCounters in the QuestionStatistics
        for (let i = 0; i < this.quizExercise.questions.length; i++) {
            this.label.push(i + 1 + '.');
            this.backgroundColor.push('#5bc0de');
            this.ratedData.push(this.quizExercise.questions[i].questionStatistic.ratedCorrectCounter);
            this.unratedData.push(this.quizExercise.questions[i].questionStatistic.unRatedCorrectCounter);
            this.ratedAverage = this.ratedAverage + (this.quizExercise.questions[i].questionStatistic.ratedCorrectCounter * this.quizExercise.questions[i].score);
            this.unratedAverage = this.unratedAverage + (this.quizExercise.questions[i].questionStatistic.unRatedCorrectCounter * this.quizExercise.questions[i].score);
        }

        // set Background for invalid questions = grey
        for (let j = 0; j < this.quizExercise.questions.length; j++) {
            if (this.quizExercise.questions[j].invalid) {
                this.backgroundColor[j] = '#949494';
            }
        }

        // add data for the last bar (Average)
        this.backgroundColor.push('#1e3368');
        this.ratedData.push(this.ratedAverage / this.maxScore);
        this.unratedData.push(this.unratedAverage / this.maxScore);

        // load data into the chart
        this.labels = this.label;
        this.colors = this.backgroundColor;

        // add Text for last label based on the language
        this.translateService.get('showStatistic.quizStatistic.average').subscribe(lastLabel => {
            this.label.push(lastLabel);
        });

        // if this.rated == true  -> load the rated data
        if (this.rated) {
            this.participants = this.quizExercise.quizPointStatistic.participantsRated;
            this.data = this.ratedData;
        } else {
            // load the unrated data
            this.participants = this.quizExercise.quizPointStatistic.participantsUnrated;
            this.data = this.unratedData;
        }
        this.datasets = [{
            data: this.data,
            backgroundColor: this.colors
        }];
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
            this.participants = this.quizExercise.quizPointStatistic.participantsUnrated;
            this.rated = false;
        } else {
            // load rated Data
            this.data = this.ratedData;
            this.participants = this.quizExercise.quizPointStatistic.participantsRated;
            this.rated = true;
        }
        this.datasets = [{
            data: this.data,
            backgroundColor: this.colors
        }];
    }

    /**
     * got to the Template with the next Statistic -> the first QuestionStatistic
     * if there is no QuestionStatistic -> go to QuizPointStatistic
     */
    nextStatistic() {
        if (this.quizExercise.questions === null || this.quizExercise.questions.length === 0) {
            this.router.navigate(['/quiz/:quizExerciseId/quiz-point-statistic', {
                quizExerciseId: this.quizExercise.id
            }]);
        } else {
            const nextQuestion = this.quizExercise.questions[0];
            if (nextQuestion.type === QuestionType.MULTIPLE_CHOICE) {
                this.router.navigate(['quiz/:quizId/multiple-choice-question-statistic/:questionId', {
                    quizId: this.quizExercise.id,
                    questionId: nextQuestion.id
                }]);
            } else if (nextQuestion.type === QuestionType.DRAG_AND_DROP) {
                this.router.navigate(['quiz/:quizId/drag-and-drop-question-statistic/:questionId', {
                    quizId: this.quizExercise.id,
                    questionId: nextQuestion.id
                }]);
            } else {
                console.log('Question type not yet supported: ' + nextQuestion);
            }
        }
    }

    /**
     * release of revoke the all statistics of the quizExercise
     *
     * @param {boolean} released: true to release, false to revoke
     */
    releaseStatistics(released: boolean) {
        this.quizStatisticUtil.releaseStatistics(released, this.quizExercise);
    }

    /**
     * check if it's allowed to release the Statistic (allowed if the quiz is finished)
     * @returns {boolean} true if it's allowed, false if not
     */
    releaseButtonDisabled() {
        this.quizStatisticUtil.releaseButtonDisabled(this.quizExercise);
    }
}
