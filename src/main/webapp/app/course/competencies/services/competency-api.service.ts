import { HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';
import { Competency, CompetencyJoLResponse, CompetencyProgress } from 'app/entities/competency.model';

@Injectable({
    providedIn: 'root',
})
export class CompetencyApiService extends BaseApiHttpService {
    // TODO: Set accessRights
    async getCompetenciesByCourseId(courseId: number): Promise<Competency[]> {
        return this.get<Competency[]>(`courses/${courseId}/competencies`);
    }

    async getCompetencyById(courseId: number, competencyId: number): Promise<Competency> {
        return this.get<Competency>(`courses/${courseId}/competencies/${competencyId}`);
    }

    async getJoL(courseId: number, competencyId: number): Promise<CompetencyJoLResponse> {
        return this.get<CompetencyJoLResponse>(`courses/${courseId}/competencies/${competencyId}/jol`);
    }

    async getCompetencyProgress(courseId: number, competencyId: number, refresh = false): Promise<CompetencyProgress> {
        let params = new HttpParams();
        params = params.set('refresh', refresh.toString());
        return this.get<CompetencyProgress>(`courses/${courseId}/competencies/${competencyId}/student-progress`, { params: params });
    }
}
