import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { TextExercise } from 'app/entities/text/text-exercise.model';
import { ExercisePagingService } from 'app/exercises/shared/manage/exercise-paging.service';

@Injectable({ providedIn: 'root' })
export class TextExercisePagingService extends ExercisePagingService<TextExercise> {
    private static readonly RESOURCE_URL = 'api/text-exercises';

    constructor(http: HttpClient) {
        super(http, TextExercisePagingService.RESOURCE_URL);
    }
}
