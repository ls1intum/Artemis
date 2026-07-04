import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { PagingService } from 'app/exercise/services/paging.service';
import { SearchResult, SearchTermPageableSearch } from 'app/foundation/pagination/pageable-table';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { SubmissionResponseDTO, fromSubmissionResponseDTO } from 'app/exercise/shared/entities/submission/submission-response.dto';

type EntityResponseType = SearchResult<Submission>;
type SubmissionSearchResponseDTO = SearchResult<SubmissionResponseDTO>;

@Injectable({ providedIn: 'root' })
export class ExampleSubmissionImportPagingService extends PagingService<Submission> {
    private http = inject(HttpClient);

    private static readonly RESOURCE_URL = 'api/exercise/exercises';

    constructor() {
        super();
    }

    /**
     * Gets all submissions with exerciseId
     * @param pageable   pageable search containing information required for pagination and sorting
     * @param options exerciseId id of exercise which submissions belongs to
     */
    override search(pageable: SearchTermPageableSearch, options: { exerciseId: number }): Observable<EntityResponseType> {
        const params = this.createHttpParams(pageable);
        return this.http
            .get<SubmissionSearchResponseDTO>(`${ExampleSubmissionImportPagingService.RESOURCE_URL}/${options.exerciseId}/submissions-for-import`, { params, observe: 'response' })
            .pipe(
                map((resp: HttpResponse<SubmissionSearchResponseDTO>) => ({
                    resultsOnPage: resp.body?.resultsOnPage.map(fromSubmissionResponseDTO) ?? [],
                    numberOfPages: resp.body?.numberOfPages ?? 0,
                })),
            );
    }
}
