import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ExercisePagingService } from 'app/exercises/shared/manage/exercise-paging.service';

@Injectable({ providedIn: 'root' })
export class ProgrammingExercisePagingService extends ExercisePagingService<ProgrammingExercise> {
    private static readonly resourceUrl = SERVER_API_URL + 'api/programming-exercises';

    constructor(http: HttpClient) {
        super(http, ProgrammingExercisePagingService.resourceUrl);
    }
}
