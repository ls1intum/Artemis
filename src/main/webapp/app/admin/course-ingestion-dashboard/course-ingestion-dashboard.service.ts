import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CourseIndexDrift, IndexOverview } from './course-ingestion-dashboard.model';

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
     * Live per-type indexed-vs-expected drift for a single course.
     */
    getCourseDrift(courseId: number): Observable<CourseIndexDrift> {
        return this.http.get<CourseIndexDrift>(`${this.baseUrl}/courses/${courseId}/index-drift`);
    }
}
