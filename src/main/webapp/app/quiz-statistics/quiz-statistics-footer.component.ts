import { Component, OnInit, OnDestroy, Input } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { QuizExercise, QuizExerciseService } from 'app/entities/quiz-exercise';
import { QuizStatisticUtil } from 'app/components/util/quiz-statistic-util.service';
import { QuizQuestion, QuizQuestionType } from 'app/entities/quiz-question';
import { MultipleChoiceQuestionStatistic } from 'app/entities/multiple-choice-question-statistic';
import { ShortAnswerQuestionUtil } from 'app/components/util/short-answer-question-util.service';
import { ArtemisMarkdown } from 'app/components/util/markdown.service';
import { AccountService, JhiWebsocketService } from 'app/core';
import { TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs/Subscription';
import { QuizPointStatistic } from 'app/entities/quiz-point-statistic';
import { HttpResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-quiz-statistics-footer',
    templateUrl: './quiz-statistics-footer.component.html',
    providers: [QuizStatisticUtil, ShortAnswerQuestionUtil, ArtemisMarkdown],
})
export class QuizStatisticsFooterComponent implements OnInit, OnDestroy {
    @Input() isQuizStatistic: boolean;

    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;

    quizExercise: QuizExercise;
    question: QuizQuestion;
    quizPointStatistic: QuizPointStatistic;
    questionStatistic: MultipleChoiceQuestionStatistic;
    questionIdParam: number;
    private sub: Subscription;
    private websocketChannelForData: string;

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
            if (this.accountService.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'])) {
                this.quizExerciseService.find(params['quizId']).subscribe((res: HttpResponse<QuizExercise>) => {
                    this.loadQuiz(res.body!);
                });
            }
        });
    }

    ngOnDestroy() {
        this.jhiWebsocketService.unsubscribe(this.websocketChannelForData);
    }

    /**
     * This functions loads the Quiz, which is necessary to build the Web-Template
     * And it loads the new Data if the Websocket has been notified
     *
     * @param {QuizExercise} quiz: the quizExercise, which the this quiz-statistic presents.
     */
    loadQuiz(quiz: QuizExercise) {
        // if the Student finds a way to the Website -> the Student will be send back to Courses
        if (!this.accountService.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR', 'ROLE_TA'])) {
            this.router.navigate(['/courses']);
        }
        this.quizExercise = quiz;
        const updatedQuestion = this.quizExercise.quizQuestions.filter(question => this.questionIdParam === question.id)[0];
        this.question = updatedQuestion as QuizQuestion;
    }

    /**
     * got to the template with the previous QuizStatistic
     * if first QuizQuestionStatistic -> go to the quiz-statistic
     */
    previousStatistic() {
        this.quizStatisticUtil.previousStatistic(this.quizExercise, this.question);
    }

    /**
     * got to the Template with the next QuizStatistic
     * if last QuizQuestionStatistic -> go to the Quiz-point-statistic
     */
    nextStatistic() {
        if (this.isQuizStatistic) {
            // go to quiz-statistic if the position = last position
            if (this.quizExercise.quizQuestions === null || this.quizExercise.quizQuestions.length === 0) {
                this.router.navigateByUrl('/quiz/' + this.quizExercise.id + '/quiz-point-statistic');
            } else {
                // go to next question-statistic
                const nextQuestion = this.quizExercise.quizQuestions[0];
                if (nextQuestion.type === this.MULTIPLE_CHOICE) {
                    this.router.navigateByUrl('/quiz/' + this.quizExercise.id + '/multiple-choice-question-statistic/' + nextQuestion.id);
                } else if (nextQuestion.type === this.DRAG_AND_DROP) {
                    this.router.navigateByUrl('/quiz/' + this.quizExercise.id + '/drag-and-drop-question-statistic/' + nextQuestion.id);
                } else if (nextQuestion.type === this.SHORT_ANSWER) {
                    this.router.navigateByUrl('/quiz/' + this.quizExercise.id + '/short-answer-question-statistic/' + nextQuestion.id);
                }
            }
        } else {
            this.quizStatisticUtil.nextStatistic(this.quizExercise, this.question);
        }
    }
}
