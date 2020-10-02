import { calculateTickMax, createOptions, DataSet, DataSetProvider } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { QuizQuestionStatistic } from 'app/entities/quiz/quiz-question-statistic.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { AccountService } from 'app/core/auth/account.service';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Authority } from 'app/shared/constants/authority.constants';
import { Subscription } from 'rxjs';
import { SafeHtml } from '@angular/platform-browser';
import { ChartOptions } from 'chart.js';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { OnDestroy, OnInit } from '@angular/core';
import { CanBecomeInvalid } from 'app/entities/quiz/drop-location.model';

export interface BackgroundColorConfig {
    backgroundColor: string;
    borderColor: string;
    pointBackgroundColor: string;
    pointBorderColor: string;
}

export abstract class QuestionStatisticComponent implements DataSetProvider, OnInit, OnDestroy {
    question: QuizQuestion;
    questionStatistic: QuizQuestionStatistic;

    quizExercise: QuizExercise;
    questionIdParam: number;
    sub: Subscription;

    chartLabels: string[] = [];
    data: number[] = [];
    chartType = 'bar';
    datasets: DataSet[] = [];

    // TODO: why do we have a second variable for labels?
    labels: string[] = [];
    // solutionLabels is currently only used for multiple choice questions
    solutionLabels: string[] = [];
    ratedData: number[] = [];
    unratedData: number[] = [];

    ratedCorrectData: number;
    unratedCorrectData: number;

    maxScore: number;
    rated = true;
    showSolution = false;
    participants: number;
    websocketChannelForData: string;

    questionTextRendered?: SafeHtml;

    // options for chart in chart.js style
    options: ChartOptions;

    backgroundColors: BackgroundColorConfig[] = [];
    backgroundSolutionColors: BackgroundColorConfig[] = [];
    colors: BackgroundColorConfig[] = [];

    constructor(
        protected route: ActivatedRoute,
        protected router: Router,
        protected accountService: AccountService,
        protected translateService: TranslateService,
        protected quizExerciseService: QuizExerciseService,
        protected jhiWebsocketService: JhiWebsocketService,
    ) {
        this.options = createOptions(this);
    }

    ngOnInit() {
        this.sub = this.route.params.subscribe((params) => {
            this.questionIdParam = +params['questionId'];
            // use different REST-call if the User is a Student
            if (this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA])) {
                this.quizExerciseService.find(params['exerciseId']).subscribe((res) => {
                    this.loadQuiz(res.body!, false);
                });
            }

            // subscribe websocket for new statistical data
            this.websocketChannelForData = '/topic/statistic/' + params['exerciseId'];
            this.jhiWebsocketService.subscribe(this.websocketChannelForData);

            // ask for new Data if the websocket for new statistical data was notified
            this.jhiWebsocketService.receive(this.websocketChannelForData).subscribe((quiz) => {
                this.loadQuiz(quiz, true);
            });

            // add Axes-labels based on selected language
            this.translateService.get('showStatistic.questionStatistic.xAxes').subscribe((xLabel) => {
                this.options.scales!.xAxes![0].scaleLabel!.labelString = xLabel;
            });
            this.translateService.get('showStatistic.questionStatistic.yAxes').subscribe((yLabel) => {
                this.options.scales!.yAxes![0].scaleLabel!.labelString = yLabel;
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
     * reset old charts data
     */
    resetLabelsColors() {
        this.labels = [];
        this.solutionLabels = [];
        this.backgroundColors = [];
        this.backgroundSolutionColors = [];
    }

    resetData() {
        this.ratedData = [];
        this.unratedData = [];
    }

    addData(rated: number, unrated: number) {
        this.ratedData.push(rated);
        this.unratedData.push(unrated);
    }

    updateData() {
        // add data for the last bar (correct Solutions)
        this.ratedCorrectData = this.questionStatistic.ratedCorrectCounter!;
        this.unratedCorrectData = this.questionStatistic.unRatedCorrectCounter!;
        this.chartLabels = this.labels;
        this.loadDataInDiagram();
    }

    /**
     * switch between showing and hiding the solution in the chart
     *  1. change the amount of participants
     *  2. change the bar-data
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

    abstract loadQuiz(quiz: QuizExercise, refresh: boolean): void;

    loadQuizCommon(quiz: QuizExercise) {
        // if the Student finds a way to the Website
        //      -> the Student will be send back to Courses
        if (!this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR, Authority.TA])) {
            this.router.navigateByUrl('courses');
        }
        // search selected question in quizExercise based on questionId
        this.quizExercise = quiz;
        const updatedQuestion = this.quizExercise.quizQuestions?.filter((question) => this.questionIdParam === question.id)[0];
        // if anyone finds a way to the Website, with a wrong combination of QuizId and QuestionId, go back to Courses
        if (!updatedQuestion) {
            this.router.navigateByUrl('courses');
            return undefined;
        }
        this.question = updatedQuestion;
        this.questionStatistic = updatedQuestion.quizQuestionStatistic!;
        return updatedQuestion;
    }

    getBackgroundColor(color: string) {
        return {
            backgroundColor: color,
            borderColor: color,
            pointBackgroundColor: color,
            pointBorderColor: color,
        };
    }

    /**
     * change label and color if an element is invalid
     */
    loadInvalidLayout(possibleInvalidElements: CanBecomeInvalid[]) {
        // set Background for invalid answers = grey
        this.translateService.get('showStatistic.invalid').subscribe((invalidLabel) => {
            possibleInvalidElements.forEach((element, i) => {
                if (element.invalid) {
                    this.backgroundColors[i] = this.getBackgroundColor('#838383');
                    this.backgroundSolutionColors[i] = this.getBackgroundColor('#838383');
                    // add 'invalid' to bar-Label
                    this.labels[i] = String.fromCharCode(65 + i) + '. ' + invalidLabel;
                }
            });
        });
    }

    /**
     * add Layout for the last bar
     */
    addLastBarLayout(length: number) {
        // add Color for last bar
        this.backgroundColors.push(this.getBackgroundColor('#5bc0de'));
        this.backgroundSolutionColors[length] = this.getBackgroundColor('#5bc0de');

        // add Text for last label based on the language
        this.translateService.get('showStatistic.quizStatistic.yAxes').subscribe((lastLabel) => {
            this.solutionLabels[length] = lastLabel.split(' ');
            this.labels[length] = lastLabel.split(' ');
            this.chartLabels = this.labels;
        });
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
            this.colors = this.backgroundSolutionColors;
            if (this.rated) {
                this.participants = this.questionStatistic.participantsRated!;
                // if rated is true use the rated Data and add the rated CorrectCounter
                this.data = this.ratedData.slice(0);
                this.data.push(this.ratedCorrectData);
            } else {
                this.participants = this.questionStatistic.participantsUnrated!;
                // if rated is false use the unrated Data and add the unrated CorrectCounter
                this.data = this.unratedData.slice(0);
                this.data.push(this.unratedCorrectData);
            }
            // show Solution
            this.chartLabels = this.solutionLabels;
        } else {
            // don't show Solution
            // if show Solution is false use the backgroundColor which doesn't show the solution
            this.colors = this.backgroundColors;
            // if rated is true use the rated Data
            if (this.rated) {
                this.participants = this.questionStatistic.participantsRated!;
                this.data = this.ratedData;
            } else {
                // if rated is false use the unrated Data
                this.participants = this.questionStatistic.participantsUnrated!;
                this.data = this.unratedData;
            }
            // don't show Solution
            this.chartLabels = this.labels;
        }

        this.datasets = [
            {
                data: this.data,
                backgroundColor: this.colors,
            },
        ];
        this.options.scales!.yAxes![0]!.ticks!.max = calculateTickMax(this);
    }
}
