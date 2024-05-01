import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { LearningPathHealthDTO } from 'app/entities/competency/learning-path-health.model';
import {
    CompetencyProgressForLearningPathDTO,
    LearningObjectType,
    LearningPathInformationDTO,
    LearningPathNavigationDto,
    NgxLearningPathDTO,
} from 'app/entities/competency/learning-path.model';
import { map, tap } from 'rxjs/operators';
import { LearningPathStorageService } from 'app/course/learning-paths/participate/learning-path-storage.service';

@Injectable({ providedIn: 'root' })
export class LearningPathService {
    private resourceURL = 'api';

    constructor(
        private httpClient: HttpClient,
        private learningPathStorageService: LearningPathStorageService,
    ) {}

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

    getLearningPathNavigation(
        learningPathId: number,
        learningObjectId: number | undefined,
        learningObjectType: LearningObjectType | undefined,
    ): Observable<HttpResponse<LearningPathNavigationDto>> {
        let params = new HttpParams();
        if (learningObjectId && learningObjectType) {
            params = params.set('learningObjectId', learningObjectId.toString());
            params = params.set('learningObjectType', learningObjectType);
        }
        return this.httpClient.get<LearningPathNavigationDto>(`${this.resourceURL}/learning-path/${learningPathId}/navigation`, { params: params, observe: 'response' });
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

    getCompetencyProgressForLearningPath(learningPathId: number) {
        return this.httpClient.get<CompetencyProgressForLearningPathDTO[]>(`${this.resourceURL}/learning-path/${learningPathId}/competency-progress`, { observe: 'response' });
    }
}
