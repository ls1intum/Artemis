import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

import { IQuizExercise } from 'app/shared/model/quiz-exercise.model';
import { QuizExerciseService } from './quiz-exercise.service';

@Component({
    selector: 'jhi-quiz-exercise-update',
    templateUrl: './quiz-exercise-update.component.html'
})
export class QuizExerciseUpdateComponent implements OnInit {
    private _quizExercise: IQuizExercise;
    isSaving: boolean;

    constructor(private quizExerciseService: QuizExerciseService, private activatedRoute: ActivatedRoute) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ quizExercise }) => {
            this.quizExercise = quizExercise;
        });
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
    get quizExercise() {
        return this._quizExercise;
    }

    set quizExercise(quizExercise: IQuizExercise) {
        this._quizExercise = quizExercise;
    }
}
