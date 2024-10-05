import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { ExercisePagingService } from 'app/exercises/shared/manage/exercise-paging.service';

@Injectable({ providedIn: 'root' })
export class QuizExercisePagingService extends ExercisePagingService<QuizExercise> {
    private static readonly RESOURCE_URL = 'api/quiz-exercises';
    constructor() {
        const http = inject(HttpClient);

        super(http, QuizExercisePagingService.RESOURCE_URL);
    }
}
