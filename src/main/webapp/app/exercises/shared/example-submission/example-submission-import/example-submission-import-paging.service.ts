import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { PageableSearch, SearchResult } from 'app/shared/table/pageable-table';
import { Submission } from 'app/entities/submission.model';

type EntityResponseType = SearchResult<Submission>;

@Injectable({ providedIn: 'root' })
export class ExampleSubmissionImportPagingService {
    constructor(private http: HttpClient) {}

    searchForSubmissions(pageable: PageableSearch, exerciseId: number): Observable<EntityResponseType> {
        const params = new HttpParams()
            .set('pageSize', String(pageable.pageSize))
            .set('page', String(pageable.page))
            .set('sortingOrder', pageable.sortingOrder)
            .set('searchTerm', pageable.searchTerm)
            .set('sortedColumn', pageable.sortedColumn);
        return this.http
            .get(`api/exercises/${exerciseId}/submissions-for-import`, { params, observe: 'response' })
            .pipe(map((resp: HttpResponse<EntityResponseType>) => resp && resp.body!));
    }
}
