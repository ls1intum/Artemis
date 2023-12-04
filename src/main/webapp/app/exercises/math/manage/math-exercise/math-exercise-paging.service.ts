import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { ExercisePagingService } from 'app/exercises/shared/manage/exercise-paging.service';
import { MathExercise } from 'app/entities/math-exercise.model';

@Injectable({ providedIn: 'root' })
export class MathExercisePagingService extends ExercisePagingService<MathExercise> {
    private static readonly resourceUrl = 'api/math-exercises';

    constructor(http: HttpClient) {
        super(http, MathExercisePagingService.resourceUrl);
    }
}
