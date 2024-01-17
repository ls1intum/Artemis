import { HttpParams } from '@angular/common/http';
import { PageableSearch, SearchResult } from 'app/shared/table/pageable-table';
import { Observable } from 'rxjs';

export abstract class PagingService<T> {
    protected createHttpParams(pageable: PageableSearch): HttpParams {
        return new HttpParams()
            .set('pageSize', String(pageable.pageSize))
            .set('page', String(pageable.page))
            .set('sortingOrder', pageable.sortingOrder)
            .set('searchTerm', pageable.searchTerm)
            .set('sortedColumn', pageable.sortedColumn);
    }

    public abstract search(pageable: PageableSearch, options?: object): Observable<SearchResult<T>>;
}
