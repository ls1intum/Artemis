import { Service, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map } from 'rxjs';

import { PageableResult, toPageableResult } from 'app/foundation/pagination/pageable-table';
import { Pageable } from 'app/openapi/model/pageable';
import { IngestionBrowserApi } from 'app/openapi/api/ingestion-browser-api';
import { IngestionCoverageApi } from 'app/openapi/api/ingestion-coverage-api';
import { IndexOverview } from 'app/openapi/model/index-overview';

import { IngestionCoverage, IngestionCoverageStatusEnum } from 'app/openapi/model/ingestion-coverage';
import { CourseBrowserData } from 'app/openapi/model/course-browser-data';
import { IndexedEntityRecord } from 'app/openapi/model/indexed-entity-record';
import { IndexedContentObject } from 'app/openapi/model/indexed-content-object';

/** Zero-based page request against a Spring `Pageable` endpoint. `sort` follows Spring's `property,direction` form. */
export interface CoveragePageRequest {
    page?: number;
    size?: number;
    sort?: string;
}

/** Page request for the live per-page view, optionally filtered by a case-insensitive course-title search. */
export interface LiveCoverageRequest extends CoveragePageRequest {
    search?: string;
}

/**
 * Page request for the stored cross-course view: the title search of the live view, plus the two filters only the
 * stored projection can answer. The search is shared because a filter and a search are one question, not two views.
 */
export interface StoredCoverageRequest extends LiveCoverageRequest {
    status?: IngestionCoverageStatusEnum;
    active?: boolean;
}

/**
 * Read-only client for the admin ingestion-observability dashboard endpoints exposed by `IngestionCoverageResource`.
 * The paged endpoints return the array body plus a total count read from the `X-Total-Count` response header, which is
 * why they observe the full response.
 */
@Service()
export class CourseIngestionDashboardService {
    private http = inject(HttpClient);
    private readonly ingestionCoverageApi = inject(IngestionCoverageApi);
    private readonly ingestionBrowserApi = inject(IngestionBrowserApi);

    private readonly baseUrl = 'api/global-search/admin';

    /** GET the index overview: Weaviate reachability + address, whether Iris is enabled, and per-collection object counts. */
    getIndexOverview(): Observable<IndexOverview> {
        return this.ingestionCoverageApi.getIndexOverview();
    }

    /**
     * GET the stored per-course coverage projection (cross-course views), paginated and sorted on the projection columns
     * (e.g. `coverageGapScore`, `releaseDate`, `lastIngestedAt`) and optionally filtered by status, by whether the course
     * is active, and by a case-insensitive title search.
     */
    getStoredCoverage(request: StoredCoverageRequest = {}): Observable<PageableResult<IngestionCoverage>> {
        return this.ingestionCoverageApi.getStoredCoverage(this.toPageable(request), request.status, request.active, request.search || undefined).pipe(map(toPageableResult));
    }

    /**
     * GET the live per-page coverage view (the default matrix view), computed on the fly for just the visible page,
     * sorted on course columns (e.g. `title`, `startDate`) and optionally filtered by a case-insensitive title search.
     */
    getLiveCoveragePage(request: LiveCoverageRequest = {}): Observable<PageableResult<IngestionCoverage>> {
        return this.ingestionCoverageApi.getLiveCoveragePage(this.toPageable(request), request.search || undefined).pipe(map(toPageableResult));
    }

    /**
     * GET everything the content browser needs to open a course: the stored entities, which units hold content per Iris
     * collection, the entities the index is missing, and the per-unit content gaps.
     *
     * One request rather than four, because all of it derives from the same two id-sets on the server and asking
     * separately made each request reload them.
     */
    getCourseBrowserData(courseId: number): Observable<CourseBrowserData> {
        return this.ingestionBrowserApi.getCourseBrowserData(courseId);
    }

    /**
     * GET the full stored records of one entity type for a course, property maps included. Fetched when a type, lecture
     * or unit is selected, because those maps carry the entity's body text and are not loaded with the course.
     */
    getIndexedEntityRecords(courseId: number, type: string): Observable<IndexedEntityRecord[]> {
        return this.ingestionBrowserApi.getIndexedEntityRecords(courseId, type);
    }

    /**
     * GET the objects stored for one lecture unit in one Iris collection. Fetched only when a collection node is
     * selected, because the property maps are heavy enough that loading them with the rest would not pay for itself.
     */
    getUnitContent(courseId: number, unitId: number, key: string): Observable<IndexedContentObject[]> {
        return this.ingestionBrowserApi.getUnitContent(courseId, unitId, key);
    }

    /** POST to force a background recompute of the whole projection (no-op if one is already running). */
    refreshCoverage(): Observable<void> {
        return this.http.post<void>(`${this.baseUrl}/coverage/refresh`, {});
    }

    /** Adapts the UI's single Spring sort criterion without overriding server pagination defaults. */
    private toPageable(request: CoveragePageRequest): Pageable {
        const pageable: Pageable = {};
        if (request.page !== undefined) {
            pageable.page = request.page;
        }
        if (request.size !== undefined) {
            pageable.size = request.size;
        }
        if (request.sort !== undefined && request.sort !== '') {
            pageable.sort = [request.sort];
        }
        return pageable;
    }
}
