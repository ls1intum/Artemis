import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { CourseSummaryDTO } from 'app/core/course/shared/entities/course-summary.model';
import { Observable } from 'rxjs';

import { CourseMaterialImportOptionsDTO, CourseMaterialImportResultDTO } from './course-material-import.model';

/**
 * Service for importing course material from one course to another.
 */
@Injectable({ providedIn: 'root' })
export class CourseMaterialImportService {
    private readonly http = inject(HttpClient);
    private readonly resourceUrl = 'api/core/courses';

    /**
     * Get a summary of what can be imported from the source course.
     *
     * @param targetCourseId the ID of the target course
     * @param sourceCourseId the ID of the source course
     * @returns Observable with the course summary
     */
    getImportSummary(targetCourseId: number, sourceCourseId: number): Observable<CourseSummaryDTO> {
        return this.http.get<CourseSummaryDTO>(`${this.resourceUrl}/${targetCourseId}/import-summary/${sourceCourseId}`);
    }

    /**
     * Import course material from the source course to the target course.
     *
     * @param targetCourseId the ID of the target course
     * @param options the import options
     * @returns Observable with the import result
     */
    importMaterial(targetCourseId: number, options: CourseMaterialImportOptionsDTO): Observable<CourseMaterialImportResultDTO> {
        return this.http.post<CourseMaterialImportResultDTO>(`${this.resourceUrl}/${targetCourseId}/import-material`, options);
    }
}
