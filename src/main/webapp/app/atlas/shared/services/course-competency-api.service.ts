import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';
import {
    CompetencyRelationDTO,
    CompetencyWithTailRelationDTO,
    CourseCompetency,
    CourseCompetencyImportOptionsDTO,
    CourseCompetencyType,
    UpdateCourseCompetencyRelationDTO,
} from 'app/atlas/shared/entities/competency.model';
import { CompetencyWithTailRelationResponseDTO, CourseCompetencyResponseDTO, toCompetency, toPrerequisite } from 'app/atlas/shared/dto/course-competency-response.dto';

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
        const response = await this.post<CompetencyWithTailRelationResponseDTO[]>(`${this.getPath(courseId)}/import-all`, courseCompetencyImportOptions);
        return response.map((entry) => ({
            competency: entry.competency
                ? entry.competency.type === CourseCompetencyType.PREREQUISITE
                    ? toPrerequisite(entry.competency)
                    : toCompetency(entry.competency)
                : undefined,
            tailRelations: entry.tailRelations,
        }));
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
        const response = await this.get<CourseCompetencyResponseDTO[]>(`${this.getPath(courseId)}`);
        return response.map((dto) => (dto.type === CourseCompetencyType.PREREQUISITE ? toPrerequisite(dto) : toCompetency(dto)));
    }

    async getSuggestedCompetencyRelations(courseId: number): Promise<SuggestCompetencyRelationsResponseDTO> {
        return await this.get<SuggestCompetencyRelationsResponseDTO>(`atlas/courses/${courseId}/competencies/relations/suggest`);
    }
}
