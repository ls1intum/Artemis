import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { PagingService } from 'app/exercise/manage/paging.service';
import { SearchResult, SearchTermPageableSearch } from 'app/shared/table/pageable-table';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

type EntityResponseType = SearchResult<Lecture>;

@Injectable({ providedIn: 'root' })
export class LecturePagingService extends PagingService<Lecture> {
    private http = inject(HttpClient);

    private static readonly RESOURCE_URL = 'api/lecture/lectures';

    constructor() {
        super();
    }

    override search(pageable: SearchTermPageableSearch): Observable<EntityResponseType> {
        const params = this.createHttpParams(pageable);
        return this.http.get(`${LecturePagingService.RESOURCE_URL}`, { params, observe: 'response' }).pipe(map((resp: HttpResponse<EntityResponseType>) => resp && resp.body!));
    }
}
