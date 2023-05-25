import { HttpParams } from '@angular/common/http';
import { PageableSearch } from 'app/shared/table/pageable-table';

export abstract class PagingService {
    protected createHttpParams(pageable: PageableSearch): HttpParams {
        return new HttpParams()
            .set('pageSize', String(pageable.pageSize))
            .set('page', String(pageable.page))
            .set('sortingOrder', pageable.sortingOrder)
            .set('searchTerm', pageable.searchTerm)
            .set('sortedColumn', pageable.sortedColumn);
    }
}
