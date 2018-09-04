import { Component, OnDestroy, OnInit } from '@angular/core';
import { QuizExercise, QuizExerciseService } from '../../entities/quiz-exercise';
import { ActivatedRoute, Router } from '@angular/router';
import { JhiWebsocketService, Principal } from '../../shared';
import { TranslateService } from '@ngx-translate/core';
import { QuizStatisticUtil } from '../../components/util/quiz-statistic-util.service';
import { ArtemisMarkdown } from '../../components/util/markdown.service';
import { MultipleChoiceQuestion } from '../../entities/multiple-choice-question';
import { MultipleChoiceQuestionStatistic } from '../../entities/multiple-choice-question-statistic';
import { QuestionType } from '../../entities/question';
import { ChartOptions } from 'chart.js';
import { createOptions, DataSet, DataSetProvider } from '../quiz-statistic/quiz-statistic.component';

@Component({
    selector: 'jhi-multiple-choice-question-statistic',
    templateUrl: './multiple-choice-question-statistic.component.html',
    providers: [QuizStatisticUtil, ArtemisMarkdown]
})
export class MultipleChoiceQuestionStatisticComponent implements OnInit, OnDestroy, DataSetProvider {

    // make constants available to html for comparison
    readonly DRAG_AND_DROP = QuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuestionType.MULTIPLE_CHOICE;

    quizExercise: QuizExercise;
    questionStatistic: MultipleChoiceQuestionStatistic;
    question: MultipleChoiceQuestion;
    questionIdParam: number;
    private sub: any;

    labels = new Array<string>();
    data = new Array<number>();
    colors = new Array<string>();
    chartType = 'bar';
    datasets = new Array<DataSet>();

    label = new Array<string>();
    solutionLabel = new Array<string>();
    ratedData = new Array<number>();
    unratedData = new Array<number>();
    backgroundColor = new Array<string>();
    backgroundSolutionColor = new Array<string>();
    ratedCorrectData: number;
    unratedCorrectData: number;

    maxScore: number;
    rated = true;
    showSolution = false;
    participants: number;
    websocketChannelForData: string;
    websocketChannelForReleaseState: string;

    questionTextRendered: string;
    answerTextRendered: string[];

    // options for chart in chart.js style
    options: ChartOptions;

    constructor(private route: ActivatedRoute,
                private router: Router,
                private principal: Principal,
                private translateService: TranslateService,
                private quizExerciseService: QuizExerciseService,
                private jhiWebsocketService: JhiWebsocketService,
                private quizStatisticUtil: QuizStatisticUtil,
                private artemisMarkdown: ArtemisMarkdown) {
        this.options = createOptions(this);
    }

    ngOnInit() {
        this.sub = this.route.params.subscribe(params => {
            this.questionIdParam = +params['questionId'];
            // use different REST-call if the User is a Student
            if (this.principal.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'])) {
                this.quizExerciseService.find(params['quizId']).subscribe(res => {
                    this.loadQuiz(res.body, false);
                });
            } else {
                this.quizExerciseService.findForStudent(params['quizId']).subscribe(res => {
                    this.loadQuiz(res.body, false);
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
                this.loadQuiz(quiz, true);
            });
            // refresh release information
            this.jhiWebsocketService.receive(this.websocketChannelForReleaseState).subscribe(payload => {
                this.quizExercise.quizPointStatistic.released = payload;
                this.questionStatistic.released = payload;
                // send students back to courses if the statistic was revoked
                if (!this.principal.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA']) && !payload) {
                    this.router.navigate(['/courses']);
                }
            });

            // add Axes-labels based on selected language
            this.translateService.get('showStatistic.multipleChoiceQuestionStatistic.xAxes').subscribe(xLabel => {
                this.options.scales.xAxes[0].scaleLabel.labelString = xLabel;
            });
            this.translateService.get('showStatistic.multipleChoiceQuestionStatistic.yAxes').subscribe(yLabel => {
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
     * This functions loads the Quiz, which is necessary to build the Web-Template
     *
     * @param {QuizExercise} quiz: the quizExercise, which the selected question is part of.
     * @param {boolean} refresh: true if method is called from Websocket
     */
    loadQuiz(quiz: QuizExercise, refresh: boolean) {
        // if the Student finds a way to the Website, while the Statistic is not released
        //      -> the Student will be send back to Courses
        if ((!this.principal.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA']))
            && quiz.quizPointStatistic.released === false) {
            this.router.navigateByUrl('courses');
        }
        // search selected question in quizExercise based on questionId
        this.quizExercise = quiz;
        const updatedQuestion = this.quizExercise.questions.filter( question => this.questionIdParam === question.id)[0];
        this.question = updatedQuestion as MultipleChoiceQuestion;
        // if the Anyone finds a way to the Website,
        // with an wrong combination of QuizId and QuestionId
        //      -> go back to Courses
        if (this.question === null) {
            this.router.navigateByUrl('courses');
        }
        this.questionStatistic = this.question.questionStatistic as MultipleChoiceQuestionStatistic;

        // load Layout only at the opening (not if the websocket refreshed the data)
        if (!refresh) {
            // render Markdown-text
            this.questionTextRendered = this.artemisMarkdown.htmlForMarkdown(this.question.text);
            this.answerTextRendered = this.question.answerOptions.map(answer => {
                return this.artemisMarkdown.htmlForMarkdown(answer.text);
            });
            this.loadLayout();
        }
        this.loadData();
    }

    /**
     * build the Chart-Layout based on the the Json-entity (questionStatistic)
     */
    loadLayout() {

        // reset old data
        this.label = [];
        this.backgroundColor = [];
        this.backgroundSolutionColor = new Array(this.question.answerOptions.length + 1);
        this.solutionLabel = new Array(this.question.answerOptions.length + 1);

        // set label and background-Color based on the AnswerOptions
        this.question.answerOptions.forEach((answerOption, i) => {
            this.label.push(String.fromCharCode(65 + i) + '.');
            this.backgroundColor.push('#428bca');
        });
        this.addLastBarLayout();
        this.loadInvalidLayout();
        this.loadSolutionLayout();
    }

    /**
     * add Layout for the last bar
     */
    addLastBarLayout() {
        // set backgroundColor for last bar
        this.backgroundColor.push('#5bc0de');
        this.backgroundSolutionColor[this.question.answerOptions.length] = '#5bc0de';

        // add Text for last label based on the language
        this.translateService.get('showStatistic.quizStatistic.yAxes').subscribe(lastLabel => {
            this.solutionLabel[this.question.answerOptions.length] = (lastLabel.split(' '));
            this.label[this.question.answerOptions.length] = (lastLabel.split(' '));
            this.labels.length = 0;
            for (let i = 0; i < this.label.length; i++) {
                this.labels.push(this.label[i]);
            }
        });
    }

    /**
     * change label and Color if a dropLocation is invalid
     */
    loadInvalidLayout() {

        // set Background for invalid answers = grey
        this.translateService.get('showStatistic.invalid').subscribe(invalidLabel => {
            this.question.answerOptions.forEach((answerOption, i) => {
                if (answerOption.invalid) {
                    this.backgroundColor[i] = '#838383';
                    this.backgroundSolutionColor[i] = '#838383';

                    this.solutionLabel[i] = String.fromCharCode(65 + i) + '. ' + invalidLabel;
                }
            });
        });
    }

    /**
     * load Layout for showSolution
     */
    loadSolutionLayout() {
        // add correct-text to the label based on the language
        this.translateService.get('showStatistic.multipleChoiceQuestionStatistic.correct')
            .subscribe(correctLabel => {
                this.question.answerOptions.forEach((answerOption, i) => {
                    if (answerOption.isCorrect) {
                        // check if the answer is valid and if true:
                        //      change solution-label and -color
                        if (!answerOption.invalid) {
                            this.backgroundSolutionColor[i] = '#5cb85c';
                            this.solutionLabel[i] = String.fromCharCode(65 + i) + '. (' + correctLabel + ')';
                        }
                    }
                });
            });

        // add incorrect-text to the label based on the language
        this.translateService.get('showStatistic.multipleChoiceQuestionStatistic.incorrect')
            .subscribe(incorrectLabel => {
                this.question.answerOptions.forEach((answerOption, i) => {
                    if (!answerOption.isCorrect) {
                        // check if the answer is valid and if false:
                        //      change solution-label and -color
                        if (!answerOption.invalid) {
                            this.backgroundSolutionColor[i] = '#d9534f';
                            this.solutionLabel[i] = String.fromCharCode(65 + i) + '. (' + incorrectLabel + ')';
                        }
                    }
                });
            });
    }

    /**
     * load the Data from the Json-entity to the chart: myChart
     */
    loadData() {

        // reset old data
        this.ratedData = [];
        this.unratedData = [];

        // set data based on the answerCounters for each AnswerOption
        this.question.answerOptions.forEach(answerOption => {
            const answerOptionCounter = this.questionStatistic.answerCounters
                .filter(answerCounter => answerOption.id === answerCounter.answer.id)[0];
            this.ratedData.push(answerOptionCounter.ratedCounter);
            this.unratedData.push(answerOptionCounter.unRatedCounter);
        });
        // add data for the last bar (correct Solutions)
        this.ratedCorrectData = this.questionStatistic.ratedCorrectCounter;
        this.unratedCorrectData = this.questionStatistic.unRatedCorrectCounter;

        this.loadDataInDiagram();
    }

    /**
     * check if the rated or unrated
     * load the rated or unrated data into the diagram
     */
    loadDataInDiagram() {
        // if show Solution is true use the
        // label, backgroundColor and Data, which show the solution
        if (this.showSolution) {
            // show Solution
            this.labels.length = 0;
            for (let i = 0; i < this.solutionLabel.length; i++) {
                this.labels.push(this.solutionLabel[i]);
            }
            // if show Solution is true use the backgroundColor which shows the solution
            this.colors.length = 0;
            for (let i = 0; i < this.backgroundSolutionColor.length; i++) {
                this.colors.push(this.backgroundSolutionColor[i]);
            }
            if (this.rated) {
                this.participants = this.questionStatistic.participantsRated;
                // if rated is true use the rated Data and add the rated CorrectCounter
                this.data = this.ratedData.slice(0);
                this.data.push(this.ratedCorrectData);
            } else {
                this.participants = this.questionStatistic.participantsUnrated;
                // if rated is false use the unrated Data and add the unrated CorrectCounter
                this.data = this.unratedData.slice(0);
                this.data.push(this.unratedCorrectData);
            }
        } else {
            // don't show Solution
            this.labels.length = 0;
            for (let i = 0; i < this.label.length; i++) {
                this.labels.push(this.label[i]);
            }
            // if show Solution is false use the backgroundColor which doesn't show the solution
            this.colors.length = 0;
            for (let i = 0; i < this.backgroundColor.length; i++) {
                this.colors.push(this.backgroundColor[i]);
            }
            // if rated is true use the rated Data
            if (this.rated) {
                this.participants = this.questionStatistic.participantsRated;
                this.data = this.ratedData;
            } else {
                // if rated is false use the unrated Data
                this.participants = this.questionStatistic.participantsUnrated;
                this.data = this.unratedData;
            }
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
        this.rated = !this.rated;
        this.loadDataInDiagram();
    }

    /**
     * switch between showing and hiding the solution in the chart
     *  1. change the BackgroundColor of the bars
     *  2. change the bar-Labels
     */
    switchSolution() {
        this.showSolution = !this.showSolution;
        this.loadDataInDiagram();
    }

    /**
     * got to the Template with the previous Statistic
     * if first QuestionStatistic -> go to the Quiz-Statistic
     */
    previousStatistic() {
        this.quizStatisticUtil.previousStatistic(this.quizExercise, this.question);
    }

    /**
     * got to the Template with the next Statistic
     * if last QuestionStatistic -> go to the Quiz-Point-Statistic
     */
    nextStatistic() {
        this.quizStatisticUtil.nextStatistic(this.quizExercise, this.question);
    }

    /**
     * release of revoke all statistics of the quizExercise
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
