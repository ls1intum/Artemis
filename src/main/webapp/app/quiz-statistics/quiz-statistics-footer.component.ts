import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { QuizExercise, QuizExerciseService } from 'app/entities/quiz-exercise';
import { QuizStatisticUtil } from 'app/components/util/quiz-statistic-util.service';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz-question';
import { MultipleChoiceQuestionStatistic } from 'app/entities/multiple-choice-question-statistic';
import { ShortAnswerQuestionUtil } from 'app/components/util/short-answer-question-util.service';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { DataSet } from 'app/quiz-statistics/quiz-statistic/quiz-statistic.component';
import { AccountService, JhiWebsocketService } from 'app/core';
import { TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs/Subscription';
import { ChartOptions } from 'chart.js';
import { QuizPointStatistic } from 'app/entities/quiz-point-statistic';
import { ShortAnswerQuestion } from 'app/entities/short-answer-question';

@Component({
    selector: 'jhi-quiz-statistics-footer',
    templateUrl: './quiz-statistics-footer.component.html',
    providers: [QuizStatisticUtil, ShortAnswerQuestionUtil, ArtemisMarkdown],
})
export class QuizStatisticsFooterComponent implements OnInit, OnDestroy {
    // @Input() quizExercise: QuizExercise;

    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;

    quizExercise: QuizExercise;
    quizPointStatistic: QuizPointStatistic;
    questionStatistic: MultipleChoiceQuestionStatistic;
    question: QuizQuestion;
    questionIdParam: number;
    private sub: Subscription;

    labels: string[] = [];
    data: number[] = [];
    colors: string[] = [];
    chartType = 'bar';
    dataSets: DataSet[] = [];

    label: string[] = [];
    solutionLabel: string[] = [];
    ratedData: number[] = [];
    unratedData: number[] = [];
    backgroundColor: string[] = [];
    backgroundSolutionColor: string[] = [];
    ratedCorrectData: number;
    unratedCorrectData: number;

    maxScore: number;
    rated = true;
    showSolution = false;
    participants: number;
    websocketChannelForData: string;
    quizExerciseChannel: string;

    // options for chart.js style
    options: ChartOptions;

    questionTextRendered: string | null;
    answerTextRendered: (string | null)[];

    // timer
    waitingForQuizStart = false;
    remainingTimeText = '?';
    remainingTimeSeconds = 0;
    interval: any;
    disconnected = true;
    onConnected: () => void;
    onDisconnected: () => void;

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        private accountService: AccountService,
        private translateService: TranslateService,
        private quizExerciseService: QuizExerciseService,
        private quizStatisticUtil: QuizStatisticUtil,
        private artemisMarkdown: ArtemisMarkdown,
        private jhiWebsocketService: JhiWebsocketService,
    ) {}

    ngOnInit() {
        this.sub = this.route.params.subscribe(params => {
            this.questionIdParam = +params['questionId'];
            // use different REST-call if the User is a Student
            if (this.accountService.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'])) {
                this.quizExerciseService.find(params['quizId']).subscribe(res => {
                    this.loadQuiz(res.body!, false);
                });
            }
        });
    }

    ngOnDestroy() {
        this.jhiWebsocketService.unsubscribe(this.websocketChannelForData);
    }

    getDataSets() {
        return this.dataSets;
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
        const updatedQuestion = this.quizExercise.quizQuestions.filter(question => this.questionIdParam === question.id)[0];
        this.question = updatedQuestion as ShortAnswerQuestion;
        // if the Anyone finds a way to the Website,
        // with an wrong combination of QuizId and QuestionId
        //      -> go back to Courses
        if (this.question === null) {
            this.router.navigateByUrl('courses');
        }
    }

    /**
     * got to the template with the previous QuizStatistic
     * if first QuizQuestionStatistic -> go to the quiz-statistic
     */
    previousStatistic() {
        this.quizStatisticUtil.previousStatistic(this.quizExercise, this.quizExercise.quizQuestions[0]);
    }

    /**
     * got to the Template with the next QuizStatistic
     * if last QuizQuestionStatistic -> go to the Quiz-point-statistic
     */
    nextStatistic() {
        console.log('this.quizExercise: ' + this.quizExercise);
        console.log('this.quizExercise.quizQuestions[0]: ' + this.quizExercise.quizQuestions[0]);
        this.quizStatisticUtil.nextStatistic(this.quizExercise, this.quizExercise.quizQuestions[0]);
    }
}
