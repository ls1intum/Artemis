import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable, map } from 'rxjs';

import { PageableResult } from 'app/foundation/pagination/pageable-table';
import { IndexOverview, IngestionCoverage, IngestionCoverageStatus } from 'app/admin/course-ingestion-dashboard/course-ingestion-dashboard.model';

/** Zero-based page request against a Spring `Pageable` endpoint. `sort` follows Spring's `property,direction` form. */
export interface CoveragePageRequest {
    page?: number;
    size?: number;
    sort?: string;
}

/** Page request for the stored cross-course view, optionally filtered by status and/or by whether the course is active. */
export interface StoredCoverageRequest extends CoveragePageRequest {
    status?: IngestionCoverageStatus;
    active?: boolean;
}

/** Page request for the live per-page view, optionally filtered by a case-insensitive course-title search. */
export interface LiveCoverageRequest extends CoveragePageRequest {
    search?: string;
}

/**
 * Read-only client for the admin ingestion-observability dashboard endpoints exposed by `IngestionCoverageResource`.
 * The paged endpoints return the array body plus a total count read from the `X-Total-Count` response header, which is
 * why they observe the full response.
 */
@Injectable({ providedIn: 'root' })
export class CourseIngestionDashboardService {
    private http = inject(HttpClient);

    // TEMPORARY (revert before merge): matches the instructor-accessible path the resource is served under.
    private readonly baseUrl = 'api/global-search/ingestion-dashboard';

    /** GET the index overview: Weaviate reachability + address, whether Iris is enabled, and per-collection object counts. */
    getIndexOverview(): Observable<IndexOverview> {
        return this.http.get<IndexOverview>(`${this.baseUrl}/index/overview`);
    }

    /**
     * GET the stored per-course coverage projection (cross-course views), paginated and sorted on the projection columns
     * (e.g. `coverageGapScore`, `releaseDate`, `lastIngestedAt`) and optionally filtered by status.
     */
    getStoredCoverage(request: StoredCoverageRequest = {}): Observable<PageableResult<IngestionCoverage>> {
        let params = this.paginationParams(request);
        if (request.status !== undefined) {
            params = params.set('status', request.status);
        }
        if (request.active !== undefined) {
            params = params.set('active', request.active);
        }
        return this.http
            .get<IngestionCoverage[]>(`${this.baseUrl}/coverage`, { params, observe: 'response' })
            .pipe(map((res) => ({ content: res.body ?? [], totalElements: Number(res.headers.get('X-Total-Count') ?? 0) })));
    }

    /**
     * GET the live per-page coverage view (the default matrix view), computed on the fly for just the visible page,
     * sorted on course columns (e.g. `title`, `startDate`) and optionally filtered by a case-insensitive title search.
     */
    getLiveCoveragePage(request: LiveCoverageRequest = {}): Observable<PageableResult<IngestionCoverage>> {
        let params = this.paginationParams(request);
        if (request.search !== undefined && request.search !== '') {
            params = params.set('search', request.search);
        }
        return this.http
            .get<IngestionCoverage[]>(`${this.baseUrl}/coverage/page`, { params, observe: 'response' })
            .pipe(map((res) => ({ content: res.body ?? [], totalElements: Number(res.headers.get('X-Total-Count') ?? 0) })));
    }

    /** POST to force a background recompute of the whole projection (no-op if one is already running). */
    refreshCoverage(): Observable<void> {
        return this.http.post<void>(`${this.baseUrl}/coverage/refresh`, {});
    }

    /** Builds the standard Spring pagination params, only setting the values that were provided. */
    private paginationParams(request: CoveragePageRequest): HttpParams {
        let params = new HttpParams();
        if (request.page !== undefined) {
            params = params.set('page', request.page);
        }
        if (request.size !== undefined) {
            params = params.set('size', request.size);
        }
        if (request.sort !== undefined && request.sort !== '') {
            params = params.set('sort', request.sort);
        }
        return params;
    }
}
