import { HttpParams } from '@angular/common/http';
import { SearchResult, SearchTermPageableSearch } from 'app/shared/table/pageable-table';
import { Observable } from 'rxjs';

export abstract class PagingService<T> {
    protected createHttpParams(pageable: SearchTermPageableSearch): HttpParams {
        return new HttpParams()
            .set('pageSize', String(pageable.pageSize))
            .set('page', String(pageable.page))
            .set('sortingOrder', pageable.sortingOrder)
            .set('searchTerm', pageable.searchTerm)
            .set('sortedColumn', pageable.sortedColumn);
    }

    public abstract search(pageable: SearchTermPageableSearch, options?: object): Observable<SearchResult<T>>;
}
