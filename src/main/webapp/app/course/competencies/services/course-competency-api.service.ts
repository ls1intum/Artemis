import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';
import { CompetencyRelationDTO, CompetencyWithTailRelationDTO, CourseCompetency, CourseCompetencyImportOptionsDTO } from 'app/entities/competency.model';

@Injectable({ providedIn: 'root' })
export class CourseCompetencyApiService extends BaseApiHttpService {
    private readonly basePath = `courses/$courseId/course-competencies`;

    private getPath(courseId: number): string {
        return this.basePath.replace('$courseId', courseId.toString());
    }

    importAllByCourseId(courseId: number, courseCompetencyImportOptions: CourseCompetencyImportOptionsDTO): Promise<CompetencyWithTailRelationDTO[]> {
        return this.post<CompetencyWithTailRelationDTO[]>(`${this.getPath(courseId)}/import-all`, courseCompetencyImportOptions);
    }

    createCourseCompetencyRelation(courseId: number, relation: CompetencyRelationDTO): Promise<CompetencyRelationDTO> {
        return this.post<CompetencyRelationDTO>(`${this.getPath(courseId)}/relations`, relation);
    }

    deleteCourseCompetencyRelation(courseId: number, relationId: number): Promise<void> {
        return this.delete<void>(`${this.getPath(courseId)}/relations/${relationId}`);
    }

    getCourseCompetencyRelations(courseId: number): Promise<CompetencyRelationDTO[]> {
        return this.get<CompetencyRelationDTO[]>(`${this.getPath(courseId)}/relations`);
    }

    getCourseCompetenciesByCourseId(courseId: number): Promise<CourseCompetency[]> {
        return this.get<CompetencyRelationDTO[]>(`${this.getPath(courseId)}`);
    }
}
