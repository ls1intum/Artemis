import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ExercisePagingService } from 'app/exercises/shared/manage/exercise-paging.service';
import { SERVER_API_URL } from 'app/environments/environment';

@Injectable({ providedIn: 'root' })
export class ModelingExercisePagingService extends ExercisePagingService<ModelingExercise> {
    private static readonly resourceUrl = SERVER_API_URL + 'api/modeling-exercises';

    constructor(http: HttpClient) {
        super(http, ModelingExercisePagingService.resourceUrl);
    }
}
