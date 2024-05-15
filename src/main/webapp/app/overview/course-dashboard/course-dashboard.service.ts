import { Injectable } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { StudentMetrics } from 'app/entities/student-metrics.model';

@Injectable({ providedIn: 'root' })
export class CourseDashboardService {
    public resourceUrl = 'api/metrics';

    constructor(private http: HttpClient) {}

    getCourseMetricsForUser(courseId: number): Observable<HttpResponse<StudentMetrics>> {
        return this.http.get<StudentMetrics>(`${this.resourceUrl}/course/${courseId}/student`, { observe: 'response' });
    }
}
