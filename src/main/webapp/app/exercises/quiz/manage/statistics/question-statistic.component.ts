import { QuizQuestion } from 'app/entities/quiz/quiz-question.model';
import { QuizQuestionStatistic } from 'app/entities/quiz/quiz-question-statistic.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { AccountService } from 'app/core/auth/account.service';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { Authority } from 'app/shared/constants/authority.constants';
import { Subscription } from 'rxjs';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { ChangeDetectorRef, Component, OnDestroy, OnInit } from '@angular/core';
import { CanBecomeInvalid } from 'app/entities/quiz/drop-location.model';
import { QuizStatistics } from 'app/exercises/quiz/manage/statistics/quiz-statistics';

export const redColor = '#d9534f';
export const greenColor = '#5cb85c';
export const blueColor = '#428bca';
export const lightBlueColor = '#5bc0de';
export const greyColor = '#838383';

@Component({ template: '' })
export abstract class QuestionStatisticComponent extends QuizStatistics implements OnInit, OnDestroy {
    question: QuizQuestion;
    questionStatistic: QuizQuestionStatistic;

    quizExercise: QuizExercise;
    questionIdParam: number;
    sub: Subscription;

    // TODO: why do we have a second variable for labels?
    labels: string[] = [];
    // solutionLabels is currently only used for multiple choice questions
    solutionLabels: string[] = [];

    ratedCorrectData: number;
    unratedCorrectData: number;

    maxScore: number;
    showSolution = false;
    websocketChannelForData: string;

    questionTextRendered?: SafeHtml;

    backgroundColors: string[] = [];
    backgroundSolutionColors: string[] = [];

    constructor(
        protected route: ActivatedRoute,
        protected router: Router,
        protected accountService: AccountService,
        protected translateService: TranslateService,
        protected quizExerciseService: QuizExerciseService,
        protected jhiWebsocketService: JhiWebsocketService,
        protected changeDetector: ChangeDetectorRef,
    ) {
        super(translateService);
        translateService.onLangChange.subscribe(() => {
            this.setAxisLabels('showStatistic.questionStatistic.xAxes', 'showStatistic.questionStatistic.yAxes');
        });
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
        //      -> the Student will be sent back to Courses
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
        const invalidLabel = this.translateService.instant('artemisApp.showStatistic.invalid');
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
        const lastLabel = this.translateService.instant('artemisApp.showStatistic.quizStatistic.yAxes');
        this.solutionLabels[length] = lastLabel;
        this.labels[length] = lastLabel;
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
            this.ngxColor.domain = this.backgroundSolutionColors;

            this.setData(this.questionStatistic);
            const additionalData = this.rated ? this.ratedCorrectData : this.unratedCorrectData;
            this.data.push(additionalData);
            // show Solution
            this.chartLabels = this.solutionLabels;
        } else {
            // don't show Solution: use the backgroundColor which doesn't show the solution
            this.ngxColor.domain = this.backgroundColors;

            this.setData(this.questionStatistic);
            // don't show Solution
            this.chartLabels = this.labels;
        }

        this.pushDataToNgxEntry(this.changeDetector);
        this.setAxisLabels('artemisApp.showStatistic.questionStatistic.xAxes', 'artemisApp.showStatistic.questionStatistic.yAxes');
    }
}
