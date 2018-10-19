import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { JhiAlertService } from 'ng-jhipster';

import { IQuizExercise } from 'app/shared/model/quiz-exercise.model';
import { QuizExerciseService } from './quiz-exercise.service';
import { IQuizPointStatistic } from 'app/shared/model/quiz-point-statistic.model';
import { QuizPointStatisticService } from 'app/entities/quiz-point-statistic';

@Component({
    selector: 'jhi-quiz-exercise-update',
    templateUrl: './quiz-exercise-update.component.html'
})
export class QuizExerciseUpdateComponent implements OnInit {
    quizExercise: IQuizExercise;
    isSaving: boolean;

    quizpointstatistics: IQuizPointStatistic[];

    constructor(
        private jhiAlertService: JhiAlertService,
        private quizExerciseService: QuizExerciseService,
        private quizPointStatisticService: QuizPointStatisticService,
        private activatedRoute: ActivatedRoute
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ quizExercise }) => {
            this.quizExercise = quizExercise;
        });
        this.quizPointStatisticService.query({ filter: 'quiz-is-null' }).subscribe(
            (res: HttpResponse<IQuizPointStatistic[]>) => {
                if (!this.quizExercise.quizPointStatistic || !this.quizExercise.quizPointStatistic.id) {
                    this.quizpointstatistics = res.body;
                } else {
                    this.quizPointStatisticService.find(this.quizExercise.quizPointStatistic.id).subscribe(
                        (subRes: HttpResponse<IQuizPointStatistic>) => {
                            this.quizpointstatistics = [subRes.body].concat(res.body);
                        },
                        (subRes: HttpErrorResponse) => this.onError(subRes.message)
                    );
                }
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.quizExercise.id !== undefined) {
            this.subscribeToSaveResponse(this.quizExerciseService.update(this.quizExercise));
        } else {
            this.subscribeToSaveResponse(this.quizExerciseService.create(this.quizExercise));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<IQuizExercise>>) {
        result.subscribe((res: HttpResponse<IQuizExercise>) => this.onSaveSuccess(), (res: HttpErrorResponse) => this.onSaveError());
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

    trackQuizPointStatisticById(index: number, item: IQuizPointStatistic) {
        return item.id;
    }
}
