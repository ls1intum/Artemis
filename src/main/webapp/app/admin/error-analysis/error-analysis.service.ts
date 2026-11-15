import { Service, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';

import { ErrorAnalysisPage, ErrorAnalysisQuery } from './error-analysis.model';

/**
 * Fetches the error analysis, which groups the errors of the server and of the clients, from the server.
 */
@Service()
export class ErrorAnalysisService {
    private readonly http = inject(HttpClient);
    private readonly resourceUrl = 'api/core/admin/error-analysis';

    /**
     * Retrieves one page of the groups of errors. The server sorts all groups before it cuts out the page, so the page follows the sort order.
     *
     * @param query what to look at, how to sort and which page to return
     * @returns the groups of the page and the totals over all pages
     */
    getGroups(query: ErrorAnalysisQuery): Observable<ErrorAnalysisPage> {
        let params = new HttpParams()
            .set('span', query.span)
            .set('minSeverity', query.minSeverity)
            .set('sortBy', query.sortField)
            .set('direction', query.ascending ? 'ASC' : 'DESC')
            .set('page', query.page)
            .set('size', query.pageSize);
        if (query.location !== 'ALL') {
            params = params.set('location', query.location);
        }
        // the server leaves out an empty list of groups
        return this.http.get<Partial<ErrorAnalysisPage>>(this.resourceUrl, { params }).pipe(
            map((page) => ({
                groups: page.groups ?? [],
                totalGroups: page.totalGroups ?? 0,
                totalErrors: page.totalErrors ?? 0,
            })),
        );
    }
}
