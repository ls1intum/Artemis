import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Unified search result interface matching the backend GlobalSearchResultDTO.
 */
export interface GlobalSearchResult {
    id: string;
    type: string;
    title: string;
    description?: string;
    badge: string;
    metadata: Record<string, any>;
}

/**
 * Service for performing global search across different entity types.
 */
@Injectable({
    providedIn: 'root',
})
export class GlobalSearchService {
    private readonly http = inject(HttpClient);
    private readonly resourceUrl = 'api/search';

    /**
     * Performs a global search query.
     *
     * @param query - The search query string
     * @param options - Optional search parameters (type filter, courseId, limit, sortBy)
     * @returns Observable of search results
     */
    search(
        query: string,
        options?: {
            type?: string;
            courseId?: number;
            limit?: number;
            sortBy?: string;
        },
    ): Observable<GlobalSearchResult[]> {
        let params = new HttpParams().set('q', query);

        if (options?.type) {
            params = params.set('type', options.type);
        }
        if (options?.courseId !== undefined) {
            params = params.set('courseId', options.courseId.toString());
        }
        if (options?.limit !== undefined) {
            params = params.set('limit', options.limit.toString());
        }
        if (options?.sortBy) {
            params = params.set('sortBy', options.sortBy);
        }

        return this.http.get<GlobalSearchResult[]>(this.resourceUrl, { params });
    }
}
