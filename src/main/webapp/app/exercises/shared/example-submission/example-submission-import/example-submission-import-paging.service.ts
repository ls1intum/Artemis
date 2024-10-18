import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Submission } from 'app/entities/submission.model';
import { PagingService } from 'app/exercises/shared/manage/paging.service';
import { SearchResult, SearchTermPageableSearch } from 'app/shared/table/pageable-table';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

type EntityResponseType = SearchResult<Submission>;

@Injectable({ providedIn: 'root' })
export class ExampleSubmissionImportPagingService extends PagingService<Submission> {
    private http = inject(HttpClient);

    private static readonly RESOURCE_URL = 'api/exercises';

    /**
     * Gets all submissions with exerciseId
     * @param pageable   pageable search containing information required for pagination and sorting
     * @param options exerciseId id of exercise which submissions belongs to
     */
    override search(pageable: SearchTermPageableSearch, options: { exerciseId: number }): Observable<EntityResponseType> {
        const params = this.createHttpParams(pageable);
        return this.http
            .get(`${ExampleSubmissionImportPagingService.RESOURCE_URL}/${options.exerciseId}/submissions-for-import`, { params, observe: 'response' })
            .pipe(map((resp: HttpResponse<EntityResponseType>) => resp && resp.body!));
    }
}
