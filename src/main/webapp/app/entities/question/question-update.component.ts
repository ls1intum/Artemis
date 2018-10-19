import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

import { IQuestion } from 'app/shared/model/question.model';
import { QuestionService } from './question.service';
import { IQuestionStatistic } from 'app/shared/model/question-statistic.model';
import { QuestionStatisticService } from 'app/entities/question-statistic';
import { IQuizExercise } from 'app/shared/model/quiz-exercise.model';
import { QuizExerciseService } from 'app/entities/quiz-exercise';

@Component({
    selector: 'jhi-question-update',
    templateUrl: './question-update.component.html'
})
export class QuestionUpdateComponent implements OnInit {
    question: IQuestion;
    isSaving: boolean;

    questionstatistics: IQuestionStatistic[];

    quizexercises: IQuizExercise[];

    constructor(
        private jhiAlertService: JhiAlertService,
        private questionService: QuestionService,
        private questionStatisticService: QuestionStatisticService,
        private quizExerciseService: QuizExerciseService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ question }) => {
            this.question = question;
        });
        this.questionStatisticService.query({ filter: 'question-is-null' }).subscribe(
            (res: HttpResponse<IQuestionStatistic[]>) => {
                if (!this.question.questionStatistic || !this.question.questionStatistic.id) {
                    this.questionstatistics = res.body;
                } else {
                    this.questionStatisticService.find(this.question.questionStatistic.id).subscribe(
                        (subRes: HttpResponse<IQuestionStatistic>) => {
                            this.questionstatistics = [subRes.body].concat(res.body);
                        },
                        (subRes: HttpErrorResponse) => this.onError(subRes.message)
                    );
                }
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
        this.quizExerciseService.query().subscribe(
            (res: HttpResponse<IQuizExercise[]>) => {
                this.quizexercises = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.question.id !== undefined) {
            this.subscribeToSaveResponse(this.questionService.update(this.question));
        } else {
            this.subscribeToSaveResponse(this.questionService.create(this.question));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IQuestion>>) {
        result.subscribe((res: HttpResponse<IQuestion>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    private onSaveError() {
        this.isSaving = false;
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }

    trackQuestionStatisticById(index: number, item: IQuestionStatistic) {
        return item.id;
    }

    trackQuizExerciseById(index: number, item: IQuizExercise) {
        return item.id;
    }
}
