import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExercisePagingService } from 'app/programming/manage/services/programming-exercise-paging.service';
import { ExercisePagingService } from 'app/exercise/manage/exercise-paging.service';

@Injectable({ providedIn: 'root' })
export class CodeAnalysisPagingService extends ExercisePagingService<ProgrammingExercise> {
    constructor() {
        const http = inject(HttpClient);

        super(http, ProgrammingExercisePagingService.RESOURCE_URL + '/with-sca');
    }
}
