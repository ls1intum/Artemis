import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Competency } from 'app/entities/competency.model';
import { PagingService } from 'app/exercises/shared/manage/paging.service';
import { SearchResult, SearchTermPageableSearch } from 'app/shared/table/pageable-table';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

type EntityResponseType = SearchResult<Competency>;

@Injectable({ providedIn: 'root' })
export class CompetencyPagingService extends PagingService<Competency> {
    public resourceUrl = 'api/competencies';

    constructor(private http: HttpClient) {
        super();
    }

    override search(pageable: SearchTermPageableSearch): Observable<EntityResponseType> {
        const params = this.createHttpParams(pageable);
        return this.http.get(`${this.resourceUrl}`, { params, observe: 'response' }).pipe(map((resp: HttpResponse<SearchResult<Competency>>) => resp && resp.body!));
    }
}
