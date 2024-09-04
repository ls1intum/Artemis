import { HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';
import { CompetencyRelationDTO, CompetencyWithTailRelationDTO, CourseCompetency } from 'app/entities/competency.model';

@Injectable({
    providedIn: 'root',
})
export class CourseCompetencyApiService extends BaseApiHttpService {
    private readonly basePath = `courses/$courseId/course-competencies`;

    private getBasePath(courseId: number): string {
        return this.basePath.replace('$courseId', courseId.toString());
    }

    async getCourseCompetenciesByCourseId(courseId: number): Promise<CourseCompetency[]> {
        return await this.get<CourseCompetency[]>(this.getBasePath(courseId));
    }

    async getCourseCompetencyRelationsByCourseId(courseId: number): Promise<CompetencyRelationDTO[]> {
        return await this.get<CompetencyRelationDTO[]>(`${this.getBasePath(courseId)}/relations`);
    }

    async deleteCourseCompetency(courseId: number, courseCompetencyId: number): Promise<void> {
        return await this.delete<void>(`${this.getBasePath(courseId)}/${courseCompetencyId}`);
    }

    async createCourseCompetencyRelation(courseId: number, courseCompetencyRelationDto: CompetencyRelationDTO): Promise<CompetencyRelationDTO> {
        return await this.post<CompetencyRelationDTO>(`${this.getBasePath(courseId)}/relations`, courseCompetencyRelationDto);
    }

    async deleteCourseCompetencyRelation(courseId: number, relationId: number): Promise<void> {
        return await this.delete<void>(`${this.getBasePath(courseId)}/relations/${relationId}`);
    }

    async importAll(courseId: number, sourceCourseId: number, importRelations: boolean): Promise<CompetencyWithTailRelationDTO[]> {
        const params = new HttpParams().set('importRelations', importRelations);
        return await this.post<CompetencyWithTailRelationDTO[]>(`${this.getBasePath(courseId)}/import-all/${sourceCourseId}`, null, {
            params: params,
        });
    }
}
