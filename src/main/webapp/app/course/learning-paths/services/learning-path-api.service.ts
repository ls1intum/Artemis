import { Injectable } from '@angular/core';
import {
    CompetencyGraphDTO,
    LearningObjectType,
    LearningPathCompetencyDTO,
    LearningPathDTO,
    LearningPathNavigationDTO,
    LearningPathNavigationObjectDTO,
    LearningPathNavigationOverviewDTO,
} from 'app/entities/competency/learning-path.model';
import { HttpParams } from '@angular/common/http';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';

@Injectable({
    providedIn: 'root',
})
export class LearningPathApiService extends BaseApiHttpService {
    async getLearningPathForCurrentUser(courseId: number): Promise<LearningPathDTO> {
        return await this.get<LearningPathDTO>(`courses/${courseId}/learning-path/me`);
    }

    async startLearningPathForCurrentUser(learningPathId: number): Promise<void> {
        return await this.patch<void>(`learning-path/${learningPathId}/start`);
    }

    async getLearningPathNavigation(learningPathId: number): Promise<LearningPathNavigationDTO> {
        return await this.get<LearningPathNavigationDTO>(`learning-path/${learningPathId}/navigation`);
    }

    async getRelativeLearningPathNavigation(
        learningPathId: number,
        learningObjectId: number,
        learningObjectType: LearningObjectType,
        competencyId: number,
    ): Promise<LearningPathNavigationDTO> {
        let params = new HttpParams();
        params = params.set('learningObjectId', learningObjectId.toString());
        params = params.set('learningObjectType', learningObjectType);
        params = params.set('competencyId', competencyId.toString());
        return await this.get<LearningPathNavigationDTO>(`learning-path/${learningPathId}/relative-navigation`, { params: params });
    }

    async generateLearningPathForCurrentUser(courseId: number): Promise<LearningPathDTO> {
        return await this.post<LearningPathDTO>(`courses/${courseId}/learning-path`);
    }

    async getLearningPathNavigationOverview(learningPathId: number): Promise<LearningPathNavigationOverviewDTO> {
        return await this.get<LearningPathNavigationOverviewDTO>(`learning-path/${learningPathId}/navigation-overview`);
    }

    async getLearningPathCompetencyGraph(learningPathId: number): Promise<CompetencyGraphDTO> {
        return await this.get<CompetencyGraphDTO>(`learning-path/${learningPathId}/competency-graph`);
    }

    async getLearningPathCompetencies(learningPathId: number): Promise<LearningPathCompetencyDTO[]> {
        return await this.get<LearningPathCompetencyDTO[]>(`learning-path/${learningPathId}/competencies`);
    }

    async getLearningPathCompetencyLearningObjects(learningPathId: number, competencyId: number): Promise<LearningPathNavigationObjectDTO[]> {
        return await this.get<LearningPathNavigationObjectDTO[]>(`learning-path/${learningPathId}/competencies/${competencyId}/learning-objects`);
    }
}
