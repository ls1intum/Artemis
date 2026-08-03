import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CourseIndexCensus, IndexOverview } from './course-ingestion-dashboard.model';

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
}
