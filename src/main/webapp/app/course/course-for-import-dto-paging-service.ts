import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { PagingService } from 'app/exercises/shared/manage/paging.service';
import { SearchResult, SearchTermPageableSearch } from 'app/shared/table/pageable-table';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { CourseForImportDTO } from 'app/entities/course.model';

type EntityResponseType = SearchResult<CourseForImportDTO>;

@Injectable({ providedIn: 'root' })
export class CourseForImportDTOPagingService extends PagingService<CourseForImportDTO> {
    private http = inject(HttpClient);

    private readonly RESOURCE_URL = 'api/courses';

    override search(pageable: SearchTermPageableSearch): Observable<EntityResponseType> {
        const params = this.createHttpParams(pageable);
        return this.http.get(`${this.RESOURCE_URL}/for-import`, { params, observe: 'response' }).pipe(map((resp: HttpResponse<EntityResponseType>) => resp && resp.body!));
    }
}
