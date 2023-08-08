import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { LearningPathHealthDTO } from 'app/entities/competency/learning-path-health.model';
import { NgxLearningPathDTO } from 'app/entities/competency/learning-path.model';
import { map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class LearningPathService {
    private resourceURL = 'api';

    constructor(private httpClient: HttpClient) {}

    enableLearningPaths(courseId: number): Observable<HttpResponse<void>> {
        return this.httpClient.put<void>(`${this.resourceURL}/courses/${courseId}/learning-paths/enable`, null, { observe: 'response' });
    }

    generateMissingLearningPathsForCourse(courseId: number): Observable<HttpResponse<void>> {
        return this.httpClient.put<void>(`${this.resourceURL}/courses/${courseId}/learning-paths/generate-missing`, null, { observe: 'response' });
    }

    getHealthStatusForCourse(courseId: number) {
        return this.httpClient.get<LearningPathHealthDTO>(`${this.resourceURL}/courses/${courseId}/learning-path-health`, { observe: 'response' });
    }

    getLearningPathNgxGraph(learningPathId: number): Observable<HttpResponse<NgxLearningPathDTO>> {
        return this.httpClient.get<NgxLearningPathDTO>(`${this.resourceURL}/learning-path/${learningPathId}/graph`, { observe: 'response' }).pipe(
            map((ngxLearningPathResponse) => {
                if (!ngxLearningPathResponse.body!.nodes) {
                    ngxLearningPathResponse.body!.nodes = [];
                }
                if (!ngxLearningPathResponse.body!.edges) {
                    ngxLearningPathResponse.body!.edges = [];
                }
                return ngxLearningPathResponse;
            }),
        );
    }
}
