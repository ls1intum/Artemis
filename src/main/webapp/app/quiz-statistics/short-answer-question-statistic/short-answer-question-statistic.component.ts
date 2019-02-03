import { Component, OnDestroy, OnInit } from '@angular/core';
import { QuizExercise, QuizExerciseService } from '../../entities/quiz-exercise';
import { ActivatedRoute, Router } from '@angular/router';
import { JhiWebsocketService, AccountService } from '../../core';
import { TranslateService } from '@ngx-translate/core';
import { QuizStatisticUtil } from '../../components/util/quiz-statistic-util.service';
import { ShortAnswerQuestionUtil } from '../../components/util/short-answer-question-util.service';
import { ArtemisMarkdown } from '../../components/util/markdown.service';
import { HttpClient } from '@angular/common/http';
import { ShortAnswerQuestion } from '../../entities/short-answer-question';
import { ShortAnswerQuestionStatistic } from '../../entities/short-answer-question-statistic';
import { QuestionType } from '../../entities/question';
import { ShortAnswerSpot } from '../../entities/short-answer-spot';
import { ChartOptions } from 'chart.js';
import { createOptions, DataSet, DataSetProvider } from '../quiz-statistic/quiz-statistic.component';
import { Subscription } from 'rxjs/Subscription';
import {ShortAnswerSolution} from 'app/entities/short-answer-solution';

interface BackgroundColorConfig {
    backgroundColor: string;
    borderColor: string;
    pointBackgroundColor: string;
    pointBorderColor: string;
}

@Component({
    selector: 'jhi-short-answer-question-statistic',
    templateUrl: './short-answer-question-statistic.component.html',
    providers: [QuizStatisticUtil, ShortAnswerQuestionUtil, ArtemisMarkdown]
})
export class ShortAnswerQuestionStatisticComponent implements OnInit, OnDestroy, DataSetProvider {
    // make constants available to html for comparison
    readonly DRAG_AND_DROP = QuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuestionType.SHORT_ANSWER;

    quizExercise: QuizExercise;
    question: ShortAnswerQuestion;
    questionStatistic: ShortAnswerQuestionStatistic;
    questionIdParam: number;
    private sub: Subscription;

    labels: string[] = [];
    data: number[] = [];
    colors: BackgroundColorConfig[] = [];
    chartType = 'bar';
    datasets: DataSet[] = [];

    label: string[] = [];
    ratedData: number[] = [];
    unratedData: number[] = [];
    backgroundColor: BackgroundColorConfig[] = [];
    backgroundSolutionColor: BackgroundColorConfig[] = [];
    ratedCorrectData: number;
    unratedCorrectData: number;

    maxScore: number;
    rated = true;
    showSolution = false;
    participants: number;
    websocketChannelForData: string;

    questionTextRendered: string;

    // options for chart in chart.js style
    options: ChartOptions;

    questionText: string;
    textWithoutSpots: string[];
    textBeforeSpots: string[];
    textAfterSpots: string[];
    isList = false;
    lettersForSolutions: number[] = [];

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private accountService: AccountService,
        private translateService: TranslateService,
        private quizExerciseService: QuizExerciseService,
        private jhiWebsocketService: JhiWebsocketService,
        private quizStatisticUtil: QuizStatisticUtil,
        private shortAnswerQuestionUtil: ShortAnswerQuestionUtil,
        private artemisMarkdown: ArtemisMarkdown,
    ) {
        this.options = createOptions(this);
    }

    ngOnInit() {
        this.sub = this.route.params.subscribe(params => {
            this.questionIdParam = +params['questionId'];
            // use different REST-call if the User is a Student
            if (this.accountService.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'])) {
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

            // ask for new Data if the websocket for new statistical data was notified
            this.jhiWebsocketService.receive(this.websocketChannelForData).subscribe(quiz => {
                this.loadQuiz(quiz, true);
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
     *
     * @param {QuizExercise} quiz: the quizExercise, which the selected question is part of.
     * @param {boolean} refresh: true if method is called from Websocket
     */
    loadQuiz(quiz: QuizExercise, refresh: boolean) {
        // if the Student finds a way to the Website
        //      -> the Student will be send back to Courses
        if (!this.accountService.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'])) {
            this.router.navigateByUrl('courses');
        }
        // search selected question in quizExercise based on questionId
        this.quizExercise = quiz;
        const updatedQuestion = this.quizExercise.questions.filter(question => this.questionIdParam === question.id)[0];
        this.question = updatedQuestion as ShortAnswerQuestion;
        // if the Anyone finds a way to the Website,
        // with an wrong combination of QuizId and QuestionId
        //      -> go back to Courses
        if (this.question === null) {
            this.router.navigateByUrl('courses');
        }
        this.questionStatistic = this.question.questionStatistic as ShortAnswerQuestionStatistic;

        // load Layout only at the opening (not if the websocket refreshed the data)
        if (!refresh) {
            this.questionTextRendered = this.artemisMarkdown.htmlForMarkdown(this.question.text);
            this.generateSaStructure();
            this.generateLettersForSolutions();

            this.loadLayout();
        }
        this.loadData();
    }

    generateSaStructure() {
        // is either '' or the question in the first line
        this.questionText = this.shortAnswerQuestionUtil.firstLineOfQuestion(this.question.text);
        this.isList = this.shortAnswerQuestionUtil.isQuestionAList(this.question.text);
        this.textWithoutSpots = this.shortAnswerQuestionUtil.getTextWithoutSpots(this.question.text);

        // separates the text into parts that come before the spot tag
        this.textBeforeSpots = this.textWithoutSpots.slice(0, this.textWithoutSpots.length - 1);

        // the last part that comes after the last spot tag
        this.textAfterSpots = this.textWithoutSpots.slice(this.textWithoutSpots.length - 1);
    }

    generateLettersForSolutions() {
        for (const mapping of this.question.correctMappings) {
            for (const i in this.question.spots) {
                if (mapping.spot.id === this.question.spots[i].id) {
                    this.lettersForSolutions.push(+i);
                    break;
                }
            }
        }
    }

    /**
     * build the Chart-Layout based on the the Json-entity (questionStatistic)
     */
    loadLayout() {

        // reset old data
        this.label = [];
        this.backgroundColor = [];
        this.backgroundSolutionColor = [];

        // set label and backgroundcolor based on the spots
        this.question.spots.forEach((spot, i) => {
            this.label.push(String.fromCharCode(65 + i) + '.');
            this.backgroundColor.push({
                backgroundColor: '#428bca',
                borderColor: '#428bca',
                pointBackgroundColor: '#428bca',
                pointBorderColor: '#428bca'
            });
            this.backgroundSolutionColor.push({
                backgroundColor: '#5cb85c',
                borderColor: '#5cb85c',
                pointBackgroundColor: '#5cb85c',
                pointBorderColor: '#5cb85c'
            });
        });

        this.addLastBarLayout();
        this.loadInvalidLayout();
    }

    /**
     * add Layout for the last bar
     */
    addLastBarLayout() {
        // add Color for last bar
        this.backgroundColor.push({
            backgroundColor: '#5bc0de',
            borderColor: '#5bc0de',
            pointBackgroundColor: '#5bc0de',
            pointBorderColor: '#5bc0de'
        });
        this.backgroundSolutionColor[this.question.spots.length] = {
            backgroundColor: '#5bc0de',
            borderColor: '#5bc0de',
            pointBackgroundColor: '#5bc0de',
            pointBorderColor: '#5bc0de'
        };

        // add Text for last label based on the language
        this.translateService.get('showStatistic.quizStatistic.yAxes').subscribe(lastLabel => {
            this.label[this.question.spots.length] = lastLabel.split(' ');
            this.labels = this.label;
        });
    }

    /**
     * change label and Color if a spot is invalid
     */
    loadInvalidLayout() {
        // set Background for invalid answers = grey
        this.translateService.get('showStatistic.invalid').subscribe(invalidLabel => {
            this.question.spots.forEach((spot, i) => {
                if (spot.invalid) {
                    this.backgroundColor[i] = {
                        backgroundColor: '#838383',
                        borderColor: '#838383',
                        pointBackgroundColor: '#838383',
                        pointBorderColor: '#838383'
                    };
                    this.backgroundSolutionColor[i] = {
                        backgroundColor: '#838383',
                        borderColor: '#838383',
                        pointBackgroundColor: '#838383',
                        pointBorderColor: '#838383'
                    };
                    // add 'invalid' to bar-Label
                    this.label[i] = String.fromCharCode(65 + i) + '. ' + invalidLabel;
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

        // set data based on the spots for each spot
        this.question.spots.forEach(spot => {
            const spotCounter = this.questionStatistic.shortAnswerSpotCounters.find(sCounter => {
                return spot.id === sCounter.spot.id;
            });
            this.ratedData.push(spotCounter.ratedCounter);
            this.unratedData.push(spotCounter.unRatedCounter);
        });
        // add data for the last bar (correct Solutions)
        this.ratedCorrectData = this.questionStatistic.ratedCorrectCounter;
        this.unratedCorrectData = this.questionStatistic.unRatedCorrectCounter;

        this.labels = this.label;

        this.loadDataInDiagram();
    }

    /**
     * check if the rated or unrated
     * load the rated or unrated data into the diagram
     */
    loadDataInDiagram() {
        // if show Solution is true use the label,
        // backgroundColor and Data, which show the solution
        if (this.showSolution) {
            // show Solution
            // if show Solution is true use the backgroundColor which shows the solution
            this.colors = this.backgroundSolutionColor;
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
            // if show Solution is false use the backgroundColor which doesn't show the solution
            this.colors = this.backgroundColor;
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

        this.datasets = [
            {
                data: this.data,
                backgroundColor: this.colors
            }
        ];
    }

    /**
     * switch between showing and hiding the solution in the chart
     */
    switchRated() {
        this.rated = !this.rated;
        this.loadDataInDiagram();
    }

    /**
     * switch between showing and hiding the solution in the chart
     *  1. change the bar-Labels
     */
    switchSolution() {
        this.showSolution = !this.showSolution;
        this.loadDataInDiagram();
    }

    /**
     * converts a number in a letter (0 -> A, 1 -> B, ...)
     *
     * @param index the given number
     */
    getLetter(index: number) {
        return String.fromCharCode(65 + index);
    }

    /**
     * Get the solution that was mapped to the given spot in the sample solution
     *
     * @param spot {object} the spot that the solution should be mapped to
     * @return {object | null} the mapped solution,
     *                          or null if no solution has been mapped to this location
     */
    correctSolutionForSpot(spot: ShortAnswerSpot) {
        const currMapping = this.shortAnswerQuestionUtil
            .solveShortAnswer(this.question, null)
            .filter(mapping => mapping.spot.id === spot.id)[0];
        if (currMapping) {
            return currMapping.solution;
        } else {
            return null;
        }
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
}
