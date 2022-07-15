import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { PageableSearch, SearchResult } from 'app/shared/table/pageable-table';
import { LearningGoal } from 'app/entities/learningGoal.model';

type EntityResponseType = SearchResult<LearningGoal>;

@Injectable({ providedIn: 'root' })
export class LearningGoalPagingService {
    public resourceUrl = SERVER_API_URL + 'api/learning-goals';

    constructor(private http: HttpClient) {}

    searchForLearningGoals(pageable: PageableSearch): Observable<EntityResponseType> {
        const params = new HttpParams()
            .set('pageSize', String(pageable.pageSize))
            .set('page', String(pageable.page))
            .set('sortingOrder', pageable.sortingOrder)
            .set('searchTerm', pageable.searchTerm)
            .set('sortedColumn', pageable.sortedColumn);
        return this.http.get(`${this.resourceUrl}`, { params, observe: 'response' }).pipe(map((resp: HttpResponse<EntityResponseType>) => resp && resp.body!));
    }
}
