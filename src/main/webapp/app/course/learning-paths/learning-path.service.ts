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
                return this.sanitizeNgxLearningPathResponse(ngxLearningPathResponse);
            }),
        );
    }

    getLearningPathNgxPath(learningPathId: number): Observable<HttpResponse<NgxLearningPathDTO>> {
        return this.httpClient.get<NgxLearningPathDTO>(`${this.resourceURL}/learning-path/${learningPathId}/path`, { observe: 'response' }).pipe(
            map((ngxLearningPathResponse) => {
                return this.sanitizeNgxLearningPathResponse(ngxLearningPathResponse);
            }),
        );
    }

    private sanitizeNgxLearningPathResponse(ngxLearningPathResponse: HttpResponse<NgxLearningPathDTO>) {
        ngxLearningPathResponse.body!.nodes ??= [];
        ngxLearningPathResponse.body!.edges ??= [];
        return ngxLearningPathResponse;
    }

    getLearningPathId(courseId: number) {
        return this.httpClient.get<number>(`${this.resourceURL}/courses/${courseId}/learning-path-id`, { observe: 'response' });
    }
}
