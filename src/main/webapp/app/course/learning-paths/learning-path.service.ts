import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { LearningPathHealthDTO } from 'app/entities/competency/learning-path-health.model';
import {
    CompetencyProgressForLearningPathDTO,
    LearningPathInformationDTO,
    LearningPathNavigationOverviewDTO,
    NgxLearningPathDTO,
} from 'app/entities/competency/learning-path.model';
import { map, tap } from 'rxjs/operators';
import { LearningPathStorageService } from 'app/course/learning-paths/participate/learning-path-storage.service';

@Injectable({ providedIn: 'root' })
export class LearningPathService {
    private httpClient = inject(HttpClient);
    private learningPathStorageService = inject(LearningPathStorageService);

    private resourceURL = 'api';

    enableLearningPaths(courseId: number): Observable<HttpResponse<void>> {
        return this.httpClient.put<void>(`${this.resourceURL}/courses/${courseId}/learning-paths/enable`, null, { observe: 'response' });
    }

    generateMissingLearningPathsForCourse(courseId: number): Observable<HttpResponse<void>> {
        return this.httpClient.put<void>(`${this.resourceURL}/courses/${courseId}/learning-paths/generate-missing`, null, { observe: 'response' });
    }

    getHealthStatusForCourse(courseId: number) {
        return this.httpClient.get<LearningPathHealthDTO>(`${this.resourceURL}/courses/${courseId}/learning-path-health`, { observe: 'response' });
    }

    getLearningPath(learningPathId: number): Observable<HttpResponse<LearningPathInformationDTO>> {
        return this.httpClient.get<LearningPathInformationDTO>(`${this.resourceURL}/learning-path/${learningPathId}`, { observe: 'response' });
    }

    getLearningPathNavigationOverview(learningPathId: number): Observable<HttpResponse<LearningPathNavigationOverviewDTO>> {
        return this.httpClient.get<LearningPathNavigationOverviewDTO>(`${this.resourceURL}/learning-path/${learningPathId}/navigation-overview`, { observe: 'response' });
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
            tap((ngxLearningPathResponse) => {
                this.learningPathStorageService.storeRecommendations(learningPathId, ngxLearningPathResponse.body!);
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

    generateLearningPath(courseId: number) {
        return this.httpClient.post<number>(`${this.resourceURL}/courses/${courseId}/learning-path`, null, { observe: 'response' });
    }

    getCompetencyProgressForLearningPath(learningPathId: number) {
        return this.httpClient.get<CompetencyProgressForLearningPathDTO[]>(`${this.resourceURL}/learning-path/${learningPathId}/competency-progress`, { observe: 'response' });
    }
}
