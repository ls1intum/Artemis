import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ExercisePagingService } from 'app/exercises/shared/manage/exercise-paging.service';

@Injectable({ providedIn: 'root' })
export class ProgrammingExercisePagingService extends ExercisePagingService<ProgrammingExercise> {
    public static readonly resourceUrl = 'api/programming-exercises';

    constructor(http: HttpClient) {
        super(http, ProgrammingExercisePagingService.resourceUrl);
    }
}
