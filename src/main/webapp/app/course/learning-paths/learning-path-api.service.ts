import { Injectable } from '@angular/core';
import { LearningObjectType, LearningPathNavigationDto } from 'app/entities/competency/learning-path.model';

@Injectable({
    providedIn: 'root',
})
export class LearningPathApiService {
    private readonly resourceURL = 'http://localhost:8080/api';

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
        const path = `${this.resourceURL}/learning-path/${learningPathId}/navigation?${params}`;
        try {
            const response = await fetch(path);
            return await response.json();
        } catch (error) {
            throw new Error(`Error while fetching learning path navigation: ${error}`);
        }
    }
}
