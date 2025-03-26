import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { ExercisePagingService } from 'app/exercise/manage/exercise-paging.service';

@Injectable({ providedIn: 'root' })
export class QuizExercisePagingService extends ExercisePagingService<QuizExercise> {
    private static readonly RESOURCE_URL = 'api/quiz/quiz-exercises';

    constructor() {
        const http = inject(HttpClient);

        super(http, QuizExercisePagingService.RESOURCE_URL);
    }
}
