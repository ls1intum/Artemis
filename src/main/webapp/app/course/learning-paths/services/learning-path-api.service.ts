import { Injectable } from '@angular/core';
import {
    CompetencyGraphDTO,
    LearningObjectType,
    LearningPathCompetencyDTO,
    LearningPathDTO,
    LearningPathInformationDTO,
    LearningPathNavigationDTO,
    LearningPathNavigationObjectDTO,
    LearningPathNavigationOverviewDTO,
    LearningPathsConfigurationDTO,
} from 'app/entities/competency/learning-path.model';
import { HttpParams } from '@angular/common/http';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';
import { LearningPathHealthDTO } from 'app/entities/competency/learning-path-health.model';
import { SearchResult, SearchTermPageableSearch } from 'app/shared/table/pageable-table';

@Injectable({
    providedIn: 'root',
})
export class LearningPathApiService extends BaseApiHttpService {
    async getLearningPathForCurrentUser(courseId: number): Promise<LearningPathDTO> {
        return await this.get<LearningPathDTO>(`atlas/courses/${courseId}/learning-path/me`);
    }

    async startLearningPathForCurrentUser(learningPathId: number): Promise<void> {
        return await this.patch<void>(`atlas/learning-path/${learningPathId}/start`);
    }

    async getLearningPathNavigation(learningPathId: number): Promise<LearningPathNavigationDTO> {
        return await this.get<LearningPathNavigationDTO>(`atlas/learning-path/${learningPathId}/navigation`);
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
        return await this.get<LearningPathNavigationDTO>(`atlas/learning-path/${learningPathId}/relative-navigation`, { params: params });
    }

    async generateLearningPathForCurrentUser(courseId: number): Promise<LearningPathDTO> {
        return await this.post<LearningPathDTO>(`atlas/courses/${courseId}/learning-path`);
    }

    async getLearningPathNavigationOverview(learningPathId: number): Promise<LearningPathNavigationOverviewDTO> {
        return await this.get<LearningPathNavigationOverviewDTO>(`atlas/learning-path/${learningPathId}/navigation-overview`);
    }

    async getLearningPathCompetencyGraph(learningPathId: number): Promise<CompetencyGraphDTO> {
        return await this.get<CompetencyGraphDTO>(`atlas/learning-path/${learningPathId}/competency-graph`);
    }

    async getLearningPathInstructorCompetencyGraph(courseId: number): Promise<CompetencyGraphDTO> {
        return await this.get<CompetencyGraphDTO>(`atlas/courses/${courseId}/learning-path/competency-instructor-graph`);
    }

    async getLearningPathCompetencies(learningPathId: number): Promise<LearningPathCompetencyDTO[]> {
        return await this.get<LearningPathCompetencyDTO[]>(`atlas/learning-path/${learningPathId}/competencies`);
    }

    async getLearningPathCompetencyLearningObjects(learningPathId: number, competencyId: number): Promise<LearningPathNavigationObjectDTO[]> {
        return await this.get<LearningPathNavigationObjectDTO[]>(`atlas/learning-path/${learningPathId}/competencies/${competencyId}/learning-objects`);
    }

    async getLearningPathsConfiguration(courseId: number): Promise<LearningPathsConfigurationDTO> {
        return await this.get<LearningPathsConfigurationDTO>(`atlas/courses/${courseId}/learning-paths/configuration`);
    }

    async getLearningPathHealthStatus(courseId: number): Promise<LearningPathHealthDTO> {
        return await this.get<LearningPathHealthDTO>(`atlas/courses/${courseId}/learning-path-health`);
    }

    async updateLearningPathsConfiguration(courseId: number, updatedLearningPathsConfiguration: LearningPathsConfigurationDTO): Promise<void> {
        await this.put<void>(`atlas/courses/${courseId}/learning-paths/configuration`, updatedLearningPathsConfiguration);
    }

    async enableLearningPaths(courseId: number): Promise<void> {
        await this.put<void>(`atlas/courses/${courseId}/learning-paths/enable`);
    }

    async generateMissingLearningPaths(courseId: number): Promise<void> {
        await this.put<void>(`atlas/courses/${courseId}/learning-paths/generate-missing`);
    }

    async getLearningPathInformation(courseId: number, pageable: SearchTermPageableSearch): Promise<SearchResult<LearningPathInformationDTO>> {
        const params = this.createHttpSearchParams(pageable);
        return await this.get<SearchResult<LearningPathInformationDTO>>(`atlas/courses/${courseId}/learning-paths`, { params });
    }
}
