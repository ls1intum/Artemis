import { Injectable } from '@angular/core';
import { LearningObjectType, LearningPathNavigationDto, LearningPathNavigationOverviewDto } from 'app/entities/competency/learning-path.model';
import { EntityNotFoundError } from 'app/course/learning-paths/exceptions/entity-not-found.error';

@Injectable({
    providedIn: 'root',
})
export class LearningPathApiService {
    private readonly resourceURL = 'api';

    private checkForErrors(response: Response): void {
        const statusCode = response.status;
        if (statusCode === 404) {
            throw new EntityNotFoundError();
        }
    }

    async getLearningPathId(courseId: number): Promise<number> {
        const response = await fetch(`${this.resourceURL}/courses/${courseId}/learning-path-id`);
        this.checkForErrors(response);
        return await response.json();
    }

    async getLearningPathNavigation(
        learningPathId: number,
        learningObjectId: number | undefined,
        learningObjectType: LearningObjectType | undefined,
    ): Promise<LearningPathNavigationDto> {
        const params = new URLSearchParams();
        if (learningObjectId && learningObjectType) {
            params.append('learningObjectId', learningObjectId.toString());
            params.append('learningObjectType', learningObjectType);
        }
        const response = await fetch(`${this.resourceURL}/learning-path/${learningPathId}/navigation?${params}`);
        this.checkForErrors(response);
        return await response.json();
    }

    async generateLearningPath(courseId: number): Promise<number> {
        const response = await fetch(`${this.resourceURL}/courses/${courseId}/learning-paths/generate`, { method: 'POST' });
        this.checkForErrors(response);
        return await response.json();
    }

    async getLearningPathNavigationOverview(learningPathId: number): Promise<LearningPathNavigationOverviewDto> {
        const response = await fetch(`${this.resourceURL}/learning-path/${learningPathId}/navigation-overview`);
        this.checkForErrors(response);
        return await response.json();
    }
}
