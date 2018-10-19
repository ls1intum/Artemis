import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

import { IQuizPointStatistic } from 'app/shared/model/quiz-point-statistic.model';
import { QuizPointStatisticService } from './quiz-point-statistic.service';
import { IQuizExercise } from 'app/shared/model/quiz-exercise.model';
import { QuizExerciseService } from 'app/entities/quiz-exercise';

@Component({
    selector: 'jhi-quiz-point-statistic-update',
    templateUrl: './quiz-point-statistic-update.component.html'
})
export class QuizPointStatisticUpdateComponent implements OnInit {
    quizPointStatistic: IQuizPointStatistic;
    isSaving: boolean;

    quizexercises: IQuizExercise[];

    constructor(
        private jhiAlertService: JhiAlertService,
        private quizPointStatisticService: QuizPointStatisticService,
        private quizExerciseService: QuizExerciseService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ quizPointStatistic }) => {
            this.quizPointStatistic = quizPointStatistic;
        });
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
        if (this.quizPointStatistic.id !== undefined) {
            this.subscribeToSaveResponse(this.quizPointStatisticService.update(this.quizPointStatistic));
        } else {
            this.subscribeToSaveResponse(this.quizPointStatisticService.create(this.quizPointStatistic));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IQuizPointStatistic>>) {
        result.subscribe((res: HttpResponse<IQuizPointStatistic>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
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

    trackQuizExerciseById(index: number, item: IQuizExercise) {
        return item.id;
    }
}
