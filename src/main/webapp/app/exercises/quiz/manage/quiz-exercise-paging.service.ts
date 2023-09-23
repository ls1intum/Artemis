import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ExercisePagingService } from 'app/exercises/shared/manage/exercise-paging.service';

@Injectable({ providedIn: 'root' })
export class QuizExercisePagingService extends ExercisePagingService<QuizExercise> {
    private static readonly resourceUrl = 'api/quiz-exercises';
    constructor(http: HttpClient) {
        super(http, QuizExercisePagingService.resourceUrl);
    }
}
