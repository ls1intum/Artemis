import { Injectable } from '@angular/core';
import { LearningObjectType, LearningPathNavigationDto, LearningPathNavigationOverviewDto } from 'app/entities/competency/learning-path.model';
import { HttpParams } from '@angular/common/http';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';

@Injectable({
    providedIn: 'root',
})
export class LearningPathApiService extends BaseApiHttpService {
    async getLearningPathId(courseId: number): Promise<number> {
        return await this.get<number>(`courses/${courseId}/learning-path-id`);
    }

    async getLearningPathNavigation(
        learningPathId: number,
        learningObjectId: number | undefined,
        learningObjectType: LearningObjectType | undefined,
    ): Promise<LearningPathNavigationDto> {
        let params = new HttpParams();
        if (learningObjectId && learningObjectType) {
            params = params.set('learningObjectId', learningObjectId.toString());
            params = params.set('learningObjectType', learningObjectType);
        }
        return await this.get<LearningPathNavigationDto>(`learning-path/${learningPathId}/navigation`, { params: params });
    }

    async generateLearningPath(courseId: number): Promise<number> {
        return await this.post<number>(`courses/${courseId}/learning-path`);
    }

    async getLearningPathNavigationOverview(learningPathId: number): Promise<LearningPathNavigationOverviewDto> {
        return await this.get<LearningPathNavigationOverviewDto>(`learning-path/${learningPathId}/navigation-overview`);
    }
}
