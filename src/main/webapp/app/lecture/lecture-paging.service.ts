import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Lecture } from 'app/entities/lecture.model';
import { PagingService } from 'app/exercises/shared/manage/paging.service';
import { PageableSearch, SearchResult } from 'app/shared/table/pageable-table';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

type EntityResponseType = SearchResult<Lecture>;

@Injectable({ providedIn: 'root' })
export class LecturePagingService extends PagingService {
    private static readonly resourceUrl = 'api/lectures';

    constructor(private http: HttpClient) {
        super();
    }

    searchForLectures(pageable: PageableSearch): Observable<EntityResponseType> {
        const params = this.createHttpParams(pageable);
        return this.http.get(`${LecturePagingService.resourceUrl}`, { params, observe: 'response' }).pipe(map((resp: HttpResponse<EntityResponseType>) => resp && resp.body!));
    }
}
