import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { PagingService } from 'app/exercises/shared/manage/paging.service';
import { PageableSearch, SearchResult } from 'app/shared/table/pageable-table';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { Course } from 'app/entities/course.model';

type EntityResponseType = SearchResult<Course>;

@Injectable({ providedIn: 'root' })
export class CoursePagingService extends PagingService<Course> {
    public resourceUrl = 'api/courses';

    constructor(private http: HttpClient) {
        super();
    }

    override search(pageable: PageableSearch): Observable<EntityResponseType> {
        const params = this.createHttpParams(pageable);
        return this.http.get(`${this.resourceUrl}/paginated`, { params, observe: 'response' }).pipe(map((resp: HttpResponse<EntityResponseType>) => resp && resp.body!));
    }
}
