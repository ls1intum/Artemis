import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { PageableSearch, SearchResult } from 'app/shared/table/pageable-table';
import { TextExercise } from 'app/entities/text-exercise.model';

type EntityResponseType = SearchResult<TextExercise>;

@Injectable({ providedIn: 'root' })
export class TextExercisePagingService {
    public resourceUrl = SERVER_API_URL + 'api/text-exercises';

    constructor(private http: HttpClient) {}

    searchForExercises(pageable: PageableSearch): Observable<EntityResponseType> {
        const params = new HttpParams()
            .set('pageSize', String(pageable.pageSize))
            .set('page', String(pageable.page))
            .set('sortingOrder', pageable.sortingOrder)
            .set('searchTerm', pageable.searchTerm)
            .set('sortedColumn', pageable.sortedColumn);
        return this.http.get(`${this.resourceUrl}`, { params, observe: 'response' }).pipe(map((resp: HttpResponse<EntityResponseType>) => resp && resp.body!));
    }
}
