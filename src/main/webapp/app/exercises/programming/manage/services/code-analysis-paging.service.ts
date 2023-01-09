import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming-exercise.model';
import { ProgrammingExercisePagingService } from 'app/exercises/programming/manage/services/programming-exercise-paging.service';
import { ExercisePagingService } from 'app/exercises/shared/manage/exercise-paging.service';
import { PageableSearch, SearchResult } from 'app/shared/table/pageable-table';
import { Observable, map } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class CodeAnalysisPagingService extends ExercisePagingService<ProgrammingExercise> {
    constructor(http: HttpClient) {
        super(http, ProgrammingExercisePagingService.resourceUrl + '/with-sca');
    }

    public searchForExercises(
        pageable: PageableSearch,
        isCourseFilter: boolean,
        isExamFilter: boolean,
        programmingLanguage?: ProgrammingLanguage,
    ): Observable<SearchResult<ProgrammingExercise>> {
        // TODO this is duplicated again, maybe the import component needs some more refactoring?
        let params = this.createHttpParams(pageable);
        params = params.set('isCourseFilter', String(isCourseFilter)).set('isExamFilter', String(isExamFilter)).set('programmingLanguage', programmingLanguage!);
        return this.http.get(`${this.resourceUrl}`, { params, observe: 'response' }).pipe(map((resp: HttpResponse<SearchResult<ProgrammingExercise>>) => resp && resp.body!));
    }

    createHttpParams(pageable: PageableSearch): HttpParams {
        const params = super.createHttpParams(pageable);
        return params.set('isSCAFilter', true);
    }
}
