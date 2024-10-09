import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';
import { CompetencyRelationDTO, CompetencyRelationType, CompetencyWithTailRelationDTO, CourseCompetency, CourseCompetencyImportOptionsDTO } from 'app/entities/competency.model';

@Injectable({ providedIn: 'root' })
export class CourseCompetencyApiService extends BaseApiHttpService {
    private readonly basePath = `courses/$courseId/course-competencies`;

    private getPath(courseId: number): string {
        return this.basePath.replace('$courseId', courseId.toString());
    }

    async importAllByCourseId(courseId: number, courseCompetencyImportOptions: CourseCompetencyImportOptionsDTO): Promise<CompetencyWithTailRelationDTO[]> {
        return await this.post<CompetencyWithTailRelationDTO[]>(`${this.getPath(courseId)}/import-all`, courseCompetencyImportOptions);
    }

    async createCourseCompetencyRelation(courseId: number, relation: CompetencyRelationDTO): Promise<CompetencyRelationDTO> {
        return await this.post<CompetencyRelationDTO>(`${this.getPath(courseId)}/relations`, relation);
    }

    async updateCourseCompetencyRelation(courseId: number, relationId: number, newRelationType: CompetencyRelationType): Promise<void> {
        return await this.patch<void>(`${this.getPath(courseId)}/relations/${relationId}`, newRelationType);
    }

    async deleteCourseCompetencyRelation(courseId: number, relationId: number): Promise<void> {
        return await this.delete<void>(`${this.getPath(courseId)}/relations/${relationId}`);
    }

    async getCourseCompetencyRelationsByCourseId(courseId: number): Promise<CompetencyRelationDTO[]> {
        return await this.get<CompetencyRelationDTO[]>(`${this.getPath(courseId)}/relations`);
    }

    async getCourseCompetencyRelations(courseId: number): Promise<CompetencyRelationDTO[]> {
        return await this.get<CompetencyRelationDTO[]>(`${this.getPath(courseId)}/relations`);
    }

    async getCourseCompetenciesByCourseId(courseId: number): Promise<CourseCompetency[]> {
        return await this.get<CompetencyRelationDTO[]>(`${this.getPath(courseId)}`);
    }
}
