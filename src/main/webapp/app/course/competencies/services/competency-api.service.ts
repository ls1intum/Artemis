import { HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';
import { CompetencyWithTailRelationDTO } from 'app/entities/competency.model';

@Injectable({
    providedIn: 'root',
})
export class CompetencyApiService extends BaseApiHttpService {
    async deleteCompetency(courseId: number, competencyId: number): Promise<void> {
        return await this.delete<void>(`courses/${courseId}/competencies/${competencyId}`);
    }

    async importAllCompetencies(courseId: number, sourceCourseId: number, importRelations: boolean): Promise<CompetencyWithTailRelationDTO[]> {
        const params = new HttpParams().set('importRelations', importRelations);
        return await this.post<CompetencyWithTailRelationDTO[]>(`courses/${courseId}/competencies/import-all/${sourceCourseId}`, null, { params: params });
    }
}
