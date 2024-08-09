import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';
import { CompetencyRelationDTO, CourseCompetency } from 'app/entities/competency.model';

@Injectable({
    providedIn: 'root',
})
export class CourseCompetencyApiService extends BaseApiHttpService {
    async getCourseCompetenciesByCourseId(courseId: number): Promise<CourseCompetency[]> {
        return await this.get<CourseCompetency[]>(`courses/${courseId}/course-competencies`);
    }

    async getCourseCompetencyRelationsByCourseId(courseId: number): Promise<CompetencyRelationDTO[]> {
        // TODO: Move to course-competencies on server
        return await this.get<CompetencyRelationDTO[]>(`courses/${courseId}/competencies/relations`);
    }
}
