import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
    ActiveIngestion,
    CourseIndexCensus,
    IndexOverview,
    IndexedContent,
    IndexedEntity,
    MissingContent,
    MissingEntity,
    PyrisReachability,
    RecentIngestion,
} from './course-ingestion-dashboard.model';

@Injectable({ providedIn: 'root' })
export class CourseIngestionDashboardService {
    private http = inject(HttpClient);

    private readonly baseUrl = 'api/global-search/admin';

    /**
     * Weaviate reachability and per-collection object counts for the ingestion dashboard.
     */
    getOverview(): Observable<IndexOverview> {
        return this.http.get<IndexOverview>(`${this.baseUrl}/index/overview`);
    }

    /**
     * Per-course, per-type index drift for every course (computed live).
     */
    getIndexCensus(): Observable<CourseIndexCensus[]> {
        return this.http.get<CourseIndexCensus[]>(`${this.baseUrl}/index-census`);
    }

    /**
     * The objects actually stored in Weaviate for a course, with their titles and ingestion times.
     */
    getIndexedEntities(courseId: number): Observable<IndexedEntity[]> {
        return this.http.get<IndexedEntity[]>(`${this.baseUrl}/courses/${courseId}/indexed-entities`);
    }

    /**
     * The objects stored in the Iris lecture-content collections (slides, transcript, unit summaries, segments).
     */
    getIndexedContent(courseId: number): Observable<IndexedContent[]> {
        return this.http.get<IndexedContent[]>(`${this.baseUrl}/courses/${courseId}/indexed-content`);
    }

    /**
     * The entities the database expects to be indexed for a course but that are absent from Weaviate, by name.
     */
    getMissingEntities(courseId: number): Observable<MissingEntity[]> {
        return this.http.get<MissingEntity[]>(`${this.baseUrl}/courses/${courseId}/missing-entities`);
    }

    /**
     * The lecture units expected to have ingested content (slides or transcript) that is absent from the Iris collections.
     */
    getContentGaps(courseId: number): Observable<MissingContent[]> {
        return this.http.get<MissingContent[]>(`${this.baseUrl}/courses/${courseId}/content-gaps`);
    }

    /**
     * The lecture ingestions currently in flight, each with its live per-step progress.
     */
    getActiveIngestions(): Observable<ActiveIngestion[]> {
        return this.http.get<ActiveIngestion[]>('api/iris/admin/lecture-ingestion/active');
    }

    /**
     * The most recently finished or failed lecture ingestions, with per-step durations and failure details.
     */
    getRecentIngestions(): Observable<RecentIngestion[]> {
        return this.http.get<RecentIngestion[]>('api/iris/admin/lecture-ingestion/recent');
    }

    /**
     * Whether the Iris service is currently reachable, for the dashboard health tile.
     */
    getPyrisHealth(): Observable<PyrisReachability> {
        return this.http.get<PyrisReachability>('api/iris/admin/pyris-health');
    }
}
