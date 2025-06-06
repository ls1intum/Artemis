import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ExercisePagingService } from 'app/exercise/services/exercise-paging.service';

@Injectable({ providedIn: 'root' })
export class ProgrammingExercisePagingService extends ExercisePagingService<ProgrammingExercise> {
    public static readonly RESOURCE_URL = 'api/programming/programming-exercises';

    constructor() {
        const http = inject(HttpClient);

        super(http, ProgrammingExercisePagingService.RESOURCE_URL);
    }
}
