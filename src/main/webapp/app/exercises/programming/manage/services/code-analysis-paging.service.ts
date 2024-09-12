import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExercisePagingService } from 'app/exercises/programming/manage/services/programming-exercise-paging.service';
import { ExercisePagingService } from 'app/exercises/shared/manage/exercise-paging.service';

@Injectable({ providedIn: 'root' })
export class CodeAnalysisPagingService extends ExercisePagingService<ProgrammingExercise> {
    constructor(http: HttpClient) {
        super(http, ProgrammingExercisePagingService.RESOURCE_URL + '/with-sca');
    }
}
