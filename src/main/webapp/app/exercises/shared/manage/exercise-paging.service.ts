import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Exercise } from 'app/entities/exercise.model';
import { PageableSearch, SearchResult } from 'app/shared/table/pageable-table';
import { Observable, map } from 'rxjs';

export abstract class ExercisePagingService<T extends Exercise> {
    protected constructor(private http: HttpClient, private resourceUrl: string) {}
    public searchForExercises(pageable: PageableSearch, isCourseFilter: boolean, isExamFilter: boolean): Observable<SearchResult<T>> {
        const params = new HttpParams()
            .set('pageSize', String(pageable.pageSize))
            .set('page', String(pageable.page))
            .set('sortingOrder', pageable.sortingOrder)
            .set('searchTerm', pageable.searchTerm)
            .set('sortedColumn', pageable.sortedColumn)
            .set('isCourseFilter', String(isCourseFilter))
            .set('isExamFilter', String(isExamFilter));
        return this.http.get(`${this.resourceUrl}`, { params, observe: 'response' }).pipe(map((resp: HttpResponse<SearchResult<T>>) => resp && resp.body!));
    }
}
