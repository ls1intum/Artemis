import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * The course-level Athena feedback configuration an instructor can edit.
 */
export interface AthenaCourseConfigDTO {
    gradingFeedbackEnabled: boolean;
    formativeFeedbackEnabled: boolean;
}

/**
 * Reads and writes the course-level Athena configuration.
 *
 * The toggles live on the course overview and in the onboarding wizard and save immediately, so they use this
 * dedicated endpoint rather than the whole-course update: a stale course settings form must not be able to overwrite
 * what was just toggled.
 */
@Injectable({ providedIn: 'root' })
export class AthenaCourseConfigService {
    private http = inject(HttpClient);

    private readonly resourceUrl = 'api/course/courses';

    /**
     * Get the Athena configuration of a course.
     *
     * @param courseId the id of the course
     */
    getCourseConfig(courseId: number): Observable<AthenaCourseConfigDTO> {
        return this.http.get<AthenaCourseConfigDTO>(`${this.resourceUrl}/${courseId}/athena-configuration`);
    }

    /**
     * Update the Athena configuration of a course.
     *
     * @param courseId the id of the course
     * @param config the configuration to store
     */
    updateCourseConfig(courseId: number, config: AthenaCourseConfigDTO): Observable<HttpResponse<AthenaCourseConfigDTO>> {
        return this.http.put<AthenaCourseConfigDTO>(`${this.resourceUrl}/${courseId}/athena-configuration`, config, { observe: 'response' });
    }
}
