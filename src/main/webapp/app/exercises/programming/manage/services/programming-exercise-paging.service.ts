import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { PageableSearch, SearchResult } from 'app/shared/table/pageable-table';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

type EntityResponseType = SearchResult<ProgrammingExercise>;

@Injectable({ providedIn: 'root' })
export class ProgrammingExercisePagingService {
    public resourceUrl = SERVER_API_URL + 'api/programming-exercises';

    constructor(private http: HttpClient) {}

    searchForExercises(pageable: PageableSearch, isCourseFilter: boolean, isExamFilter: boolean): Observable<EntityResponseType> {
        const params = new HttpParams()
            .set('pageSize', String(pageable.pageSize))
            .set('page', String(pageable.page))
            .set('sortingOrder', pageable.sortingOrder)
            .set('searchTerm', pageable.searchTerm)
            .set('sortedColumn', pageable.sortedColumn)
            .set('isCourseFilter', String(isCourseFilter))
            .set('isExamFilter', String(isExamFilter));
        return this.http.get(`${this.resourceUrl}`, { params, observe: 'response' }).pipe(map((resp: HttpResponse<EntityResponseType>) => resp && resp.body!));
    }
}
