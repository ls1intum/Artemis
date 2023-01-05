import { HttpClient, HttpResponse } from '@angular/common/http';
import { Exercise } from 'app/entities/exercise.model';
import { PagingService } from 'app/exercises/shared/manage/paging.service';
import { PageableSearch, SearchResult } from 'app/shared/table/pageable-table';
import { Observable, map } from 'rxjs';

export abstract class ExercisePagingService<T extends Exercise> extends PagingService {
    protected constructor(protected http: HttpClient, private resourceUrl: string) {
        super();
    }
    public searchForExercises(pageable: PageableSearch, isCourseFilter: boolean, isExamFilter: boolean): Observable<SearchResult<T>> {
        let params = this.createParams(pageable);
        params = params.set('isCourseFilter', String(isCourseFilter)).set('isExamFilter', String(isExamFilter));
        return this.http.get(`${this.resourceUrl}`, { params, observe: 'response' }).pipe(map((resp: HttpResponse<SearchResult<T>>) => resp && resp.body!));
    }
}
