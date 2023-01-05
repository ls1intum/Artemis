import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExercisePagingService } from 'app/exercises/programming/manage/services/programming-exercise-paging.service';
import { ExercisePagingService } from 'app/exercises/shared/manage/exercise-paging.service';
import { PageableSearch } from 'app/shared/table/pageable-table';

@Injectable({ providedIn: 'root' })
export class CodeAnalysisPagingService extends ExercisePagingService<ProgrammingExercise> {
    constructor(http: HttpClient) {
        super(http, ProgrammingExercisePagingService.resourceUrl);
    }

    createParams(pageable: PageableSearch): HttpParams {
        const params = super.createParams(pageable);
        return params.set('isSCAFilter', true);
    }
}
