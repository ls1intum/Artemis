import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { LearningGoal } from 'app/entities/learningGoal.model';
import { PagingService } from 'app/exercises/shared/manage/paging.service';
import { PageableSearch, SearchResult } from 'app/shared/table/pageable-table';

type EntityResponseType = SearchResult<LearningGoal>;

@Injectable({ providedIn: 'root' })
export class LearningGoalPagingService extends PagingService {
    public resourceUrl = SERVER_API_URL + 'api/learning-goals';

    constructor(private http: HttpClient) {
        super();
    }

    searchForLearningGoals(pageable: PageableSearch): Observable<EntityResponseType> {
        const params = this.createHttpParams(pageable);
        return this.http.get(`${this.resourceUrl}`, { params, observe: 'response' }).pipe(map((resp: HttpResponse<EntityResponseType>) => resp && resp.body!));
    }
}
