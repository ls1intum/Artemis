import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { NgxLearningPathDTO } from 'app/entities/learning-path.model';
import { map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class LearningPathService {
    private resourceURL = 'api';

    constructor(private httpClient: HttpClient) {}

    enableLearningPaths(courseId: number): Observable<HttpResponse<Course>> {
        return this.httpClient.put<Course>(`${this.resourceURL}/courses/${courseId}/learning-paths/enable`, null, { observe: 'response' });
    }

    getNgxLearningPath(courseId: number): Observable<HttpResponse<NgxLearningPathDTO>> {
        return this.httpClient.get<NgxLearningPathDTO>(`${this.resourceURL}/courses/${courseId}/learning-path-graph`, { observe: 'response' }).pipe(
            map((ngxLearningPathResponse) => {
                if (!ngxLearningPathResponse.body!.nodes) {
                    ngxLearningPathResponse.body!.nodes = [];
                }
                if (!ngxLearningPathResponse.body!.edges) {
                    ngxLearningPathResponse.body!.edges = [];
                }
                if (!ngxLearningPathResponse.body!.clusters) {
                    ngxLearningPathResponse.body!.clusters = [];
                }
                return ngxLearningPathResponse;
            }),
        );
    }
}
