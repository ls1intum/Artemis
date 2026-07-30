import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ScienceEnabledCourse, ScienceResearchExportAudit, ScienceResearchExportRequest } from 'app/admin/science/admin-science.model';

@Injectable({ providedIn: 'root' })
export class AdminScienceService {
    private readonly http = inject(HttpClient);
    private readonly resourceUrl = 'api/atlas/admin/science';

    getCourses(): Observable<ScienceEnabledCourse[]> {
        return this.http.get<ScienceEnabledCourse[]>(`${this.resourceUrl}/courses`);
    }

    enableCourse(courseId: number): Observable<ScienceEnabledCourse> {
        return this.http.put<ScienceEnabledCourse>(`${this.resourceUrl}/courses/${courseId}`, {});
    }

    disableCourse(courseId: number): Observable<ScienceEnabledCourse> {
        return this.http.delete<ScienceEnabledCourse>(`${this.resourceUrl}/courses/${courseId}`);
    }

    getExportAudits(): Observable<ScienceResearchExportAudit[]> {
        return this.http.get<ScienceResearchExportAudit[]>(`${this.resourceUrl}/export-audits`);
    }

    createExport(request: ScienceResearchExportRequest): Observable<Blob> {
        return this.http.post(`${this.resourceUrl}/exports`, request, { responseType: 'blob' });
    }
}
