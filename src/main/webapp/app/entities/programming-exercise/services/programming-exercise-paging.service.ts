import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { SERVER_API_URL } from 'app/app.constants';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { PageableSearch, SearchResult } from 'app/entities/programming-exercise/programming-exercise-import.component';

type EntityResponseType = HttpResponse<SearchResult>;

@Injectable({ providedIn: 'root' })
export class ProgrammingExercisePagingService {
    public resourceUrl = SERVER_API_URL + 'api/programming-exercises';

    constructor(private http: HttpClient) {}

    searchForExercises(pageable: PageableSearch): Observable<SearchResult> {
        const params = new HttpParams()
            .set('pageSize', String(pageable.pageSize))
            .set('page', String(pageable.page))
            .set('sortingOrder', pageable.sortingOrder)
            .set('partialTitle', pageable.partialTitle)
            .set('sortColumn', pageable.sortColumn);
        return this.http.get(`${this.resourceUrl}/pageable`, { params, observe: 'response' }).pipe(map((resp: EntityResponseType) => resp && resp.body!));
    }
}
