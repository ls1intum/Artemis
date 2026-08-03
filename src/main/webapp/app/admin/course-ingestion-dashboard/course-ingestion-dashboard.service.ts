import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ActiveIngestion, CourseIndexCensus, IndexOverview, RecentIngestion } from './course-ingestion-dashboard.model';

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
}
