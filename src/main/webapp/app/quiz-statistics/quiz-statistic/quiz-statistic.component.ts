import { Component, OnDestroy, OnInit } from '@angular/core';
import { QuizExercise, QuizExerciseService } from '../../entities/quiz-exercise';
import { ActivatedRoute, Router } from '@angular/router';
import { AccountService, JhiWebsocketService } from '../../core';
import { TranslateService } from '@ngx-translate/core';
import { HttpResponse } from '@angular/common/http';
import { Chart, ChartAnimationOptions, ChartOptions } from 'chart.js';
import { QuestionType } from '../../entities/question';
import { Subscription } from 'rxjs/Subscription';

const Sugar = require('sugar');
Sugar.extend();
require('sugar/string/truncateOnWord');
require('sugar/polyfills/es7');

export interface DataSet {
    data: Array<number>;
    backgroundColor: Array<any>;
}

export interface ChartElement {
    chart: Chart;
}

// this code is reused in 4 different statistic components
export function createOptions(dataSetProvider: DataSetProvider): ChartOptions {
    return {
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
            fontSize: 16,
            padding: 20
        },
        tooltips: {
            enabled: false
        },
        scales: {
            yAxes: [
                {
                    scaleLabel: {
                        labelString: '',
                        display: true
                    },
                    ticks: {
                        beginAtZero: true
                    }
                }
            ],
            xAxes: [
                {
                    scaleLabel: {
                        labelString: '',
                        display: true
                    }
                }
            ]
        },
        hover: { animationDuration: 0 },
        animation: createAnimation(dataSetProvider)
    };
}

// this code is reused in 4 different statistic components
export function createAnimation(dataSetProvider: DataSetProvider): ChartAnimationOptions {
    return {
        duration: 500,
        onComplete: (chartElement: ChartElement) => {
            const chart = chartElement.chart;
            const ctx = chart.ctx;
            const fontSize = 12;
            const fontStyle = 'normal';
            const fontFamily = 'Arial';
            ctx.font = Chart.helpers.fontString(fontSize, fontStyle, fontFamily);
            ctx.textAlign = 'center';
            ctx.textBaseline = 'middle';

            const participants = dataSetProvider.getParticipants();
            dataSetProvider.getDataSets().forEach((dataset, i) => {
                const meta = chart.getDatasetMeta(i);
                meta.data.forEach((bar: any, index) => {
                    const data = (Math.round(dataset.data[index] * 100) / 100).toString();
                    const dataPercentage = Math.round((dataset.data[index] / participants) * 1000) / 10;
                    const position = bar.tooltipPosition();

                    // if the bar is high enough -> write the percentageValue inside the bar
                    if (dataPercentage > 6) {
                        // if the bar is low enough -> write the amountValue above the bar
                        if (position.y > 15) {
                            ctx.fillStyle = 'black';
                            ctx.fillText(data, position.x, position.y - 10);

                            if (participants !== 0) {
                                ctx.fillStyle = 'white';
                                ctx.fillText(dataPercentage.toString() + '%', position.x, position.y + 10);
                            }
                        } else {
                            // if the bar is too high -> write the amountValue inside the bar
                            ctx.fillStyle = 'white';
                            if (participants !== 0) {
                                ctx.fillText(data + ' / ' + dataPercentage.toString() + '%', position.x, position.y + 10);
                            } else {
                                ctx.fillText(data, position.x, position.y + 10);
                            }
                        }
                    } else {
                        // if the bar is to low -> write the percentageValue above the bar
                        ctx.fillStyle = 'black';
                        if (participants !== 0) {
                            ctx.fillText(data + ' / ' + dataPercentage.toString() + '%', position.x, position.y - 10);
                        } else {
                            ctx.fillText(data, position.x, position.y - 10);
                        }
                    }
                });
            });
        }
    };
}

export interface DataSetProvider {
    getDataSets(): DataSet[];
    getParticipants(): number;
}

@Component({
    selector: 'jhi-quiz-statistic',
    templateUrl: './quiz-statistic.component.html'
})
export class QuizStatisticComponent implements OnInit, OnDestroy, DataSetProvider {
    // make constants available to html for comparison
    readonly DRAG_AND_DROP = QuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuestionType.SHORT_ANSWER;

    quizExercise: QuizExercise;
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
    ratedAverage: number;
    unratedAverage: number;

    maxScore: number;
    rated = true;
    participants: number;
    websocketChannelForData: string;

    // options for chart.js style
    options: ChartOptions;

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private accountService: AccountService,
        private translateService: TranslateService,
        private quizExerciseService: QuizExerciseService,
        private jhiWebsocketService: JhiWebsocketService,
    ) {
        this.options = createOptions(this);
    }

    ngOnInit() {
        this.sub = this.route.params.subscribe(params => {
            // use different REST-call if the User is a Student
            if (this.accountService.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'])) {
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

            // ask for new Data if the websocket for new statistical data was notified
            this.jhiWebsocketService.receive(this.websocketChannelForData).subscribe(() => {
                if (this.accountService.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'])) {
                    this.quizExerciseService.find(params['quizId']).subscribe(res => {
                        this.loadQuizSuccess(res.body);
                    });
                } else {
                    this.quizExerciseService.findForStudent(params['quizId']).subscribe(res => {
                        this.loadQuizSuccess(res.body);
                    });
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
    }

    getDataSets() {
        return this.datasets;
    }

    getParticipants() {
        return this.participants;
    }

    /**
     * This functions loads the Quiz, which is necessary to build the Web-Template
     * And it loads the new Data if the Websocket has been notified
     *
     * @param {QuizExercise} quiz: the quizExercise, which the this quiz-statistic presents.
     */
    loadQuizSuccess(quiz: QuizExercise) {
        // if the Student finds a way to the Website -> the Student will be send back to Courses
        if (!this.accountService.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'])) {
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
            this.ratedAverage =
                this.ratedAverage +
                this.quizExercise.questions[i].questionStatistic.ratedCorrectCounter * this.quizExercise.questions[i].score;
            this.unratedAverage =
                this.unratedAverage +
                this.quizExercise.questions[i].questionStatistic.unRatedCorrectCounter * this.quizExercise.questions[i].score;
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
        this.datasets = [
            {
                data: this.data,
                backgroundColor: this.colors
            }
        ];
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
        this.datasets = [
            {
                data: this.data,
                backgroundColor: this.colors
            }
        ];
    }

    /**
     * got to the Template with the next Statistic -> the first QuestionStatistic
     * if there is no QuestionStatistic -> go to QuizPointStatistic
     */
    nextStatistic() {
        if (this.quizExercise.questions === null || this.quizExercise.questions.length === 0) {
            this.router.navigate([
                '/quiz/:quizExerciseId/quiz-point-statistic',
                {
                    quizExerciseId: this.quizExercise.id
                }
            ]);
        } else {
            const nextQuestion = this.quizExercise.questions[0];
            if (nextQuestion.type === QuestionType.MULTIPLE_CHOICE) {
                this.router.navigate([
                    'quiz/:quizId/multiple-choice-question-statistic/:questionId',
                    {
                        quizId: this.quizExercise.id,
                        questionId: nextQuestion.id
                    }
                ]);
            } else if (nextQuestion.type === QuestionType.DRAG_AND_DROP) {
                this.router.navigate([
                    'quiz/:quizId/drag-and-drop-question-statistic/:questionId',
                    {
                        quizId: this.quizExercise.id,
                        questionId: nextQuestion.id
                    }
                ]);
            } else if (nextQuestion.type === QuestionType.SHORT_ANSWER) {
                this.router.navigate([
                    'quiz/:quizId/short-answer-question-statistic/:questionId',
                    {
                        quizId: this.quizExercise.id,
                        questionId: nextQuestion.id
                    }
                ]);
            } else {
                console.log('Question type not yet supported: ' + nextQuestion);
            }
        }
    }

}
