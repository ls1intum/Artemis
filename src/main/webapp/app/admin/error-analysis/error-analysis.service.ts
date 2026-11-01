import { Service, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ErrorAnalysisSpan, ErrorGroup, ErrorLocationFilter, ErrorSeverityFilter } from './error-analysis.model';

/**
 * Fetches the error analysis, which groups the errors of the server and of the clients, from the server.
 */
@Service()
export class ErrorAnalysisService {
    private readonly http = inject(HttpClient);
    private readonly resourceUrl = 'api/core/admin/error-analysis';

    /**
     * Retrieves the groups of errors that happened in a time span.
     *
     * @param span how far back to look
     * @param location where the errors happened, `ALL` for the server and the clients
     * @param minSeverity the least serious errors to show, which also shows the more serious ones
     * @returns the groups, the most frequent first
     */
    getGroups(span: ErrorAnalysisSpan, location: ErrorLocationFilter, minSeverity: ErrorSeverityFilter): Observable<ErrorGroup[]> {
        let params = new HttpParams().set('span', span).set('minSeverity', minSeverity);
        if (location !== 'ALL') {
            params = params.set('location', location);
        }
        return this.http.get<ErrorGroup[]>(this.resourceUrl, { params });
    }
}
