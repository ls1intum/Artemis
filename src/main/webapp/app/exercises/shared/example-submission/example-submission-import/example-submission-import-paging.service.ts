import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Submission } from 'app/entities/submission.model';
import { PagingService } from 'app/exercises/shared/manage/paging.service';
import { PageableSearch, SearchResult } from 'app/shared/table/pageable-table';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

type EntityResponseType = SearchResult<Submission>;

@Injectable({ providedIn: 'root' })
export class ExampleSubmissionImportPagingService extends PagingService {
    private static readonly resourceUrl = 'api/exercises';

    constructor(private http: HttpClient) {
        super();
    }

    /**
     * Gets all submissions with exerciseId
     * @param pageable   pageable search containing information required for pagination and sorting
     * @param exerciseId id of exercise which submissions belongs to
     */
    searchForSubmissions(pageable: PageableSearch, exerciseId: number): Observable<EntityResponseType> {
        const params = this.createHttpParams(pageable);
        return this.http
            .get(`${ExampleSubmissionImportPagingService.resourceUrl}/${exerciseId}/submissions-for-import`, { params, observe: 'response' })
            .pipe(map((resp: HttpResponse<EntityResponseType>) => resp && resp.body!));
    }
}
