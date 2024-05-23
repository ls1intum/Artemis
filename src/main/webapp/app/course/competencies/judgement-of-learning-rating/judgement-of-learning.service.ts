import { HttpClient, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class JudgementOfLearningService {
    private resourceURL = 'api';

    constructor(private httpClient: HttpClient) {}

    /**
     * Sets the judgment of learning for a competency.
     *
     * @param courseId The ID of the course.
     * @param competencyId The ID of the competency.
     * @param jolValue The judgment of learning value.
     * @returns An Observable of the HTTP response.
     */
    setJudgementOfLearning(courseId: number, competencyId: number, jolValue: number): Observable<HttpResponse<void>> {
        return this.httpClient.put<void>(`${this.resourceURL}/courses/${courseId}/competencies/${competencyId}/jol/${jolValue}`, null, { observe: 'response' });
    }
}
