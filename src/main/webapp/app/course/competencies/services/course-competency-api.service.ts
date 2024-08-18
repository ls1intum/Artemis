import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';
import { CompetencyRelation, CompetencyRelationDTO, CompetencyWithTailRelationDTO, CourseCompetencyImportOptionsDTO } from 'app/entities/competency.model';

@Injectable({ providedIn: 'root' })
export class CourseCompetencyApiService extends BaseApiHttpService {
    private readonly basePath = `courses/$courseId/course-competencies`;

    private getPath(courseId: number): string {
        return this.basePath.replace('$courseId', courseId.toString());
    }

    importAllByCourseId(courseId: number, courseCompetencyImportOptions: CourseCompetencyImportOptionsDTO): Promise<CompetencyWithTailRelationDTO[]> {
        return this.post<CompetencyWithTailRelationDTO[]>(`${this.getPath(courseId)}/import-all`, courseCompetencyImportOptions);
    }

    createCourseCompetencyRelation(courseId: number, relation: CompetencyRelation): Promise<CompetencyRelationDTO> {
        return this.post<CompetencyRelationDTO>(`${this.getPath(courseId)}/relations`, relation);
    }
}
