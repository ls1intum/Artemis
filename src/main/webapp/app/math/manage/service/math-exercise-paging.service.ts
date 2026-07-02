import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { MathExercise } from 'app/math/shared/entities/math-exercise.model';
import { ExercisePagingService } from 'app/exercise/services/exercise-paging.service';

@Injectable({ providedIn: 'root' })
export class MathExercisePagingService extends ExercisePagingService<MathExercise> {
    private static readonly RESOURCE_URL = 'api/math/math-exercises';

    constructor() {
        const http = inject(HttpClient);
        super(http, MathExercisePagingService.RESOURCE_URL);
    }
}
