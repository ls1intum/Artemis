import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ExercisePagingService } from 'app/exercises/shared/manage/exercise-paging.service';

@Injectable({ providedIn: 'root' })
export class ModelingExercisePagingService extends ExercisePagingService<ModelingExercise> {
    private static readonly RESOURCE_URL = 'api/modeling-exercises';

    constructor(http: HttpClient) {
        super(http, ModelingExercisePagingService.RESOURCE_URL);
    }
}
