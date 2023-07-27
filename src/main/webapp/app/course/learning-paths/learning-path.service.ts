import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Course } from 'app/entities/course.model';
import { LearningPathRecommendationDTO, NgxLearningPathDTO } from 'app/entities/competency/learning-path.model';
import { map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class LearningPathService {
    private resourceURL = 'api';

    constructor(private httpClient: HttpClient) {}

    enableLearningPaths(courseId: number): Observable<HttpResponse<void>> {
        return this.httpClient.put<void>(`${this.resourceURL}/courses/${courseId}/learning-paths/enable`, null, { observe: 'response' });
    }

    getLearningPathId(courseId: number) {
        return this.httpClient.get<number>(`${this.resourceURL}/courses/${courseId}/learning-path-id`, { observe: 'response' });
    }

    getNgxLearningPath(learningPathId: number): Observable<HttpResponse<NgxLearningPathDTO>> {
        return this.httpClient.get<NgxLearningPathDTO>(`${this.resourceURL}/learning-path/${learningPathId}`, { observe: 'response' }).pipe(
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

    getRecommendation(learningPathId: number) {
        return this.httpClient.get<LearningPathRecommendationDTO>(`${this.resourceURL}/learning-path/${learningPathId}/recommendation`, { observe: 'response' });
    }
}
