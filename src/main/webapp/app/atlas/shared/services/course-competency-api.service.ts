import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';
import {
    CompetencyRelationDTO,
    CompetencyWithTailRelationDTO,
    CourseCompetency,
    CourseCompetencyImportOptionsDTO,
    UpdateCourseCompetencyRelationDTO,
} from 'app/atlas/shared/entities/competency.model';

interface SuggestCompetencyRelationsResponseDTO {
    relations: { tail_id: string; head_id: string; relation_type: string }[];
}

@Injectable({ providedIn: 'root' })
export class CourseCompetencyApiService extends BaseApiHttpService {
    private readonly basePath = `atlas/courses/$courseId/course-competencies`;

    private getPath(courseId: number): string {
        return this.basePath.replace('$courseId', courseId.toString());
    }

    async importAllByCourseId(courseId: number, courseCompetencyImportOptions: CourseCompetencyImportOptionsDTO): Promise<CompetencyWithTailRelationDTO[]> {
        return await this.post<CompetencyWithTailRelationDTO[]>(`${this.getPath(courseId)}/import-all`, courseCompetencyImportOptions);
    }

    async createCourseCompetencyRelation(courseId: number, relation: CompetencyRelationDTO): Promise<CompetencyRelationDTO> {
        return await this.post<CompetencyRelationDTO>(`${this.getPath(courseId)}/relations`, relation);
    }

    async updateCourseCompetencyRelation(courseId: number, relationId: number, updateCourseCompetencyRelationDTO: UpdateCourseCompetencyRelationDTO): Promise<void> {
        return await this.patch<void>(`${this.getPath(courseId)}/relations/${relationId}`, updateCourseCompetencyRelationDTO);
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

    async getSuggestedCompetencyRelations(courseId: number): Promise<SuggestCompetencyRelationsResponseDTO> {
        return await this.get<SuggestCompetencyRelationsResponseDTO>(`atlas/courses/${courseId}/competencies/relations/suggest`);
    }
}
