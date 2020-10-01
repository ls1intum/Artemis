import { Component, OnDestroy, OnInit, ViewEncapsulation } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { QuizStatisticUtil } from 'app/exercises/quiz/shared/quiz-statistic-util.service';
import { DragAndDropQuestionUtil } from 'app/exercises/quiz/shared/drag-and-drop-question-util.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { ChartOptions } from 'chart.js';
import { calculateTickMax, createOptions, DataSet, DataSetProvider } from '../quiz-statistic/quiz-statistic.component';
import { Subscription } from 'rxjs/Subscription';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { DragAndDropQuestionStatistic } from 'app/entities/quiz/drag-and-drop-question-statistic.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { Authority } from 'app/shared/constants/authority.constants';
import { QuizQuestionStatistic } from 'app/entities/quiz/quiz-question-statistic.model';

export interface BackgroundColorConfig {
    backgroundColor: string;
    borderColor: string;
    pointBackgroundColor: string;
    pointBorderColor: string;
}

export abstract class QuestionStatisticComponent implements DataSetProvider {
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
        // if the Anyone finds a way to the Website,
        // with a wrong combination of QuizId and QuestionId
        //      -> go back to Courses
        if (!updatedQuestion) {
            this.router.navigateByUrl('courses');
        }
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

@Component({
    selector: 'jhi-drag-and-drop-question-statistic',
    templateUrl: './drag-and-drop-question-statistic.component.html',
    providers: [QuizStatisticUtil, DragAndDropQuestionUtil, ArtemisMarkdownService],
    styleUrls: ['./drag-and-drop-question-statistic.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class DragAndDropQuestionStatisticComponent extends QuestionStatisticComponent implements OnInit, OnDestroy {
    question: DragAndDropQuestion;
    questionStatistic: DragAndDropQuestionStatistic;

    constructor(
        route: ActivatedRoute,
        router: Router,
        accountService: AccountService,
        translateService: TranslateService,
        quizExerciseService: QuizExerciseService,
        jhiWebsocketService: JhiWebsocketService,
        quizStatisticUtil: QuizStatisticUtil,
        private dragAndDropQuestionUtil: DragAndDropQuestionUtil,
        private artemisMarkdown: ArtemisMarkdownService,
    ) {
        super(route, router, accountService, translateService, quizExerciseService, jhiWebsocketService);
    }

    ngOnInit() {
        super.init();
    }

    ngOnDestroy() {
        super.destroy();
    }

    /**
     * This functions loads the Quiz, which is necessary to build the Web-Template
     *
     * @param {QuizExercise} quiz: the quizExercise, which the selected question is part of.
     * @param {boolean} refresh: true if method is called from Websocket
     */
    loadQuiz(quiz: QuizExercise, refresh: boolean) {
        const updatedQuestion = super.loadQuizCommon(quiz);
        this.question = updatedQuestion as DragAndDropQuestion;
        this.questionStatistic = this.question.quizQuestionStatistic as DragAndDropQuestionStatistic;

        // load Layout only at the opening (not if the websocket refreshed the data)
        if (!refresh) {
            this.questionTextRendered = this.artemisMarkdown.safeHtmlForMarkdown(this.question.text);
            this.loadLayout();
        }
        this.loadData();
    }

    /**
     * build the Chart-Layout based on the the Json-entity (questionStatistic)
     */
    loadLayout() {
        this.orderDropLocationByPos();

        // reset old data
        this.labels = [];
        this.backgroundColors = [];
        this.backgroundSolutionColors = [];

        // set label and backgroundcolor based on the dropLocations
        this.question.dropLocations!.forEach((dropLocation, i) => {
            this.labels.push(String.fromCharCode(65 + i) + '.');
            this.backgroundColors.push(this.getBackgroundColor('#428bca'));
            this.backgroundSolutionColors.push(this.getBackgroundColor('#5cb85c'));
        });

        this.addLastBarLayout();
        this.loadInvalidLayout();
    }

    /**
     * add Layout for the last bar
     */
    addLastBarLayout() {
        // add Color for last bar
        this.backgroundColors.push(this.getBackgroundColor('#5bc0de'));
        this.backgroundSolutionColors[this.question.dropLocations!.length] = this.getBackgroundColor('#5bc0de');

        // add Text for last label based on the language
        this.translateService.get('showStatistic.quizStatistic.yAxes').subscribe((lastLabel) => {
            this.labels[this.question.dropLocations!.length] = lastLabel.split(' ');
            this.chartLabels = this.labels;
        });
    }

    /**
     * change label and Color if a dropLocation is invalid
     */
    loadInvalidLayout() {
        // set Background for invalid answers = grey
        this.translateService.get('showStatistic.invalid').subscribe((invalidLabel) => {
            this.question.dropLocations!.forEach((dropLocation, i) => {
                if (dropLocation.invalid) {
                    this.backgroundColors[i] = this.getBackgroundColor('#838383');
                    this.backgroundSolutionColors[i] = this.getBackgroundColor('#838383');
                    // add 'invalid' to bar-Label
                    this.labels[i] = String.fromCharCode(65 + i) + '. ' + invalidLabel;
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

        // set data based on the dropLocations for each dropLocation
        this.question.dropLocations!.forEach((dropLocation) => {
            const dropLocationCounter = this.questionStatistic.dropLocationCounters?.find((dlCounter) => dropLocation.id === dlCounter.dropLocation!.id)!;
            this.ratedData.push(dropLocationCounter.ratedCounter!);
            this.unratedData.push(dropLocationCounter.unRatedCounter!);
        });
        // add data for the last bar (correct Solutions)
        this.ratedCorrectData = this.questionStatistic.ratedCorrectCounter!;
        this.unratedCorrectData = this.questionStatistic.unRatedCorrectCounter!;

        this.chartLabels = this.labels;

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
     * order DropLocations by Position
     */
    orderDropLocationByPos() {
        let change = true;
        while (change) {
            change = false;
            if (this.question.dropLocations && this.question.dropLocations.length > 0) {
                for (let i = 0; i < this.question.dropLocations.length - 1; i++) {
                    if (this.question.dropLocations[i].posX! > this.question.dropLocations[i + 1].posX!) {
                        // switch DropLocations
                        const temp = this.question.dropLocations[i];
                        this.question.dropLocations[i] = this.question.dropLocations[i + 1];
                        this.question.dropLocations[i + 1] = temp;
                        change = true;
                    }
                }
            }
        }
    }

    /**
     * Get the drag item that was mapped to the given drop location in the sample solution
     *
     * @param dropLocation {object} the drop location that the drag item should be mapped to
     * @return {object | null} the mapped drag item,
     *                          or null if no drag item has been mapped to this location
     */
    correctDragItemForDropLocation(dropLocation: DropLocation) {
        const currMapping = this.dragAndDropQuestionUtil.solve(this.question, undefined).filter((mapping) => mapping.dropLocation!.id === dropLocation.id)[0];
        if (currMapping) {
            return currMapping.dragItem;
        } else {
            return null;
        }
    }
}
