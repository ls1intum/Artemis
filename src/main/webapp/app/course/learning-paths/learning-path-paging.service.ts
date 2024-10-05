import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { PagingService } from 'app/exercises/shared/manage/paging.service';
import { SearchResult, SearchTermPageableSearch } from 'app/shared/table/pageable-table';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { LearningPathInformationDTO } from 'app/entities/competency/learning-path.model';

type EntityResponseType = SearchResult<LearningPathInformationDTO>;
@Injectable({ providedIn: 'root' })
export class LearningPathPagingService extends PagingService<LearningPathInformationDTO> {
    private http = inject(HttpClient);

    public resourceUrl = 'api';

    override search(pageable: SearchTermPageableSearch, options: { courseId: number }): Observable<EntityResponseType> {
        const params = this.createHttpParams(pageable);
        return this.http
            .get(`${this.resourceUrl}/courses/${options.courseId}/learning-paths`, { params, observe: 'response' })
            .pipe(map((resp: HttpResponse<EntityResponseType>) => resp && resp.body!));
    }
}
