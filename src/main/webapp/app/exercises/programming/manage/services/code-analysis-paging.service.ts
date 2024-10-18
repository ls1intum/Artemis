import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExercisePagingService } from 'app/exercises/programming/manage/services/programming-exercise-paging.service';
import { ExercisePagingService } from 'app/exercises/shared/manage/exercise-paging.service';

@Injectable({ providedIn: 'root' })
export class CodeAnalysisPagingService extends ExercisePagingService<ProgrammingExercise> {
    constructor() {
        const http = inject(HttpClient);

        super(http, ProgrammingExercisePagingService.RESOURCE_URL + '/with-sca');
    }
}
