import { calculateHeightOfChart, createOptions, DataSet, DataSetProvider } from 'app/exercises/quiz/manage/statistics/quiz-statistic/quiz-statistic.component';
import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { QuizQuestionStatistic } from 'app/entities/quiz/quiz-question-statistic.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { AccountService } from 'app/core/auth/account.service';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Authority } from 'app/shared/constants/authority.constants';
import { Subscription } from 'rxjs';
import { SafeHtml } from '@angular/platform-browser';
import { ChartOptions, ChartType } from 'chart.js';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { CanBecomeInvalid } from 'app/entities/quiz/drop-location.model';
import { BaseChartDirective, Color } from 'ng2-charts';
import { ScaleType, Color as NgxColor } from '@swimlane/ngx-charts';
import { QuizStatisticsDirective } from 'app/exercises/quiz/manage/statistics/quiz-statistics.directive';

export const redColor = '#d9534f';
export const greenColor = '#5cb85c';
export const blueColor = '#428bca';
export const lightBlueColor = '#5bc0de';
export const greyColor = '#838383';

@Component({ template: '' })
export abstract class QuestionStatisticComponent extends QuizStatisticsDirective implements DataSetProvider, OnInit, OnDestroy {
    @ViewChild(BaseChartDirective) chart: BaseChartDirective;

    question: QuizQuestion;
    questionStatistic: QuizQuestionStatistic;

    quizExercise: QuizExercise;
    questionIdParam: number;
    sub: Subscription;

    /*chartLabels: string[] = [];
    data: number[] = [];*/
    chartType: ChartType = 'bar';
    datasets: DataSet[] = [];

    // TODO: why do we have a second variable for labels?
    labels: string[] = [];
    // solutionLabels is currently only used for multiple choice questions
    solutionLabels: string[] = [];
    /*ratedData: number[] = [];
    unratedData: number[] = [];*/

    ratedCorrectData: number;
    unratedCorrectData: number;

    maxScore: number;
    // rated = true;
    showSolution = false;
    // participants: number;
    websocketChannelForData: string;

    questionTextRendered?: SafeHtml;

    options: ChartOptions;

    backgroundColors: string[] = [];
    backgroundSolutionColors: string[] = [];
    colors: Color[] = [];

    // ngx
    /*ngxData: any[] = [];
    ngxColor = {
        name: 'question statistics',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: [],
    } as NgxColor;
    xAxisLabel = this.translateService.instant('showStatistic.questionStatistic.xAxes');
    yAxisLabel = this.translateService.instant('showStatistic.questionStatistic.yAxes');
    maxScale: number;*/

    constructor(
        protected route: ActivatedRoute,
        protected router: Router,
        protected accountService: AccountService,
        protected translateService: TranslateService,
        protected quizExerciseService: QuizExerciseService,
        protected jhiWebsocketService: JhiWebsocketService,
    ) {
        super();
    }

    ngOnInit() {
        this.sub = this.route.params.subscribe((params) => {
            this.questionIdParam = +params['questionId'];
            // use different REST-call if the User is a Student
            if (this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA])) {
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

    /**
     * converts a number in a letter (0 -> A, 1 -> B, ...)
     *
     * @param index the given number
     */
    getLetter(index: number) {
        return String.fromCharCode(65 + index);
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
        if (!this.accountService.hasAnyAuthorityDirect([Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA])) {
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

    /**
     * change label and color if an element is invalid
     */
    loadInvalidLayout(possibleInvalidElements: CanBecomeInvalid[]) {
        // set Background for invalid answers = grey
        const invalidLabel = this.translateService.instant('showStatistic.invalid');
        possibleInvalidElements.forEach((element, i) => {
            if (element.invalid) {
                this.backgroundColors[i] = greyColor;
                this.backgroundSolutionColors[i] = greyColor;
                // add 'invalid' to bar-Label
                this.labels[i] = this.getLetter(i) + '. ' + invalidLabel;
            }
        });
    }

    /**
     * add Layout for the last bar
     */
    addLastBarLayout(length: number) {
        // add Color for last bar
        this.backgroundColors.push(lightBlueColor);
        this.backgroundSolutionColors[length] = lightBlueColor;

        // add Text for last label based on the language
        const lastLabel = this.translateService.instant('showStatistic.quizStatistic.yAxes');
        this.solutionLabels[length] = lastLabel; // lastLabel.split(' ');
        this.labels[length] = lastLabel; // lastLabel.split(' ');
        this.chartLabels = this.labels;
    }

    /**
     * check if the rated or unrated, then load the rated or unrated data into the diagram
     */
    loadDataInDiagram() {
        this.ngxColor.domain = [];
        // if show Solution is true use the label, backgroundColor and Data, which show the solution
        if (this.showSolution) {
            // show Solution: use the backgroundColor which shows the solution
            this.colors = [{ backgroundColor: this.backgroundSolutionColors }];
            this.ngxColor.domain = this.backgroundSolutionColors;

            /*if (this.rated) {
                this.participants = this.questionStatistic.participantsRated!;
                // if rated is true use the rated Data and add the rated CorrectCounter
                this.data = [...this.ratedData];
                // additionally show how many people on average have the complete answer correct (which should only be shown when the solution is displayed)
                this.data.push(this.ratedCorrectData);
            } else {
                this.participants = this.questionStatistic.participantsUnrated!;
                // if rated is false use the unrated Data and add the unrated CorrectCounter
                this.data = [...this.unratedData];
                // additionally show how many people on average have the complete answer correct (which should only be shown when the solution is displayed)
                this.data.push(this.unratedCorrectData);
            }*/
            this.setData(this.questionStatistic);
            const additionalData = this.rated ? this.ratedCorrectData : this.unratedCorrectData;
            this.data.push(additionalData);
            // show Solution
            this.chartLabels = this.solutionLabels;
        } else {
            // don't show Solution: use the backgroundColor which doesn't show the solution
            this.colors = [{ backgroundColor: this.backgroundColors }];
            this.ngxColor.domain = this.backgroundColors;

            // if rated is true use the rated Data
            /*if (this.rated) {
                this.participants = this.questionStatistic.participantsRated!;
                this.data = [...this.ratedData];
            } else {
                // if rated is false use the unrated Data
                this.participants = this.questionStatistic.participantsUnrated!;
                this.data = [...this.unratedData];
            }*/
            this.setData(this.questionStatistic);
            // don't show Solution
            this.chartLabels = this.labels;
        }

        /*this.ngxData = [];
        this.datasets = [{ data: this.data, backgroundColor: this.colors.map((color) => color.backgroundColor as string) }];
        this.data.forEach((score, index) => {
            this.ngxData.push({ name: this.chartLabels[index], value: score });
        });
        // this.colors.forEach((color) => this.ngxColor.domain.push(color.backgroundColor as string));
        // recalculate the height of the chart because rated/unrated might have changed or new results might have appeared
        const height = calculateHeightOfChart(this);
        this.maxScale = calculateHeightOfChart(this);
        // add Axes-labels based on selected language
        const xLabel = this.translateService.instant('showStatistic.questionStatistic.xAxes');
        const yLabel = this.translateService.instant('showStatistic.questionStatistic.yAxes');
        this.options = createOptions(this, height, height / 5, xLabel, yLabel);
        if (this.chart) {
            this.chart.update(0);
        }*/
        this.pushDataToNgxEntry();
        this.xAxisLabel = this.translateService.instant('showStatistic.questionStatistic.xAxes');
        this.yAxisLabel = this.translateService.instant('showStatistic.questionStatistic.yAxes');
        // this.ngxData = [...this.ngxData];
    }
}
