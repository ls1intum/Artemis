import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';

@Injectable({ providedIn: 'root' })
export class LearningPathService {
    private resourceURL = 'api';

    constructor(private httpClient: HttpClient) {}

    enableLearningPaths(courseId: number): Observable<HttpResponse<Course>> {
        return this.httpClient.put<Course>(`${this.resourceURL}/courses/${courseId}/learning-paths/enable`, null, { observe: 'response' });
    }
}
