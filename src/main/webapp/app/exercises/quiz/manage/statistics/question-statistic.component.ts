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

export interface BackgroundColorConfig {
    backgroundColor: string;
    borderColor: string;
    pointBackgroundColor: string;
    pointBorderColor: string;
}

export abstract class QuestionStatisticComponent implements DataSetProvider {
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

    init() {
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

    destroy() {
        this.jhiWebsocketService.unsubscribe(this.websocketChannelForData);
    }

    getDataSets() {
        return this.datasets;
    }

    getParticipants() {
        return this.participants;
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
