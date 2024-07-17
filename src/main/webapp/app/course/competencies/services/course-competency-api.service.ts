import { HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { BaseApiHttpService } from 'app/course/learning-paths/services/base-api-http.service';
import { CompetencyJoLResponse, CompetencyProgress, CourseCompetency } from 'app/entities/competency.model';

@Injectable({
    providedIn: 'root',
})
export class CourseCompetencyApiService extends BaseApiHttpService {
    private readonly resourceUrl = '{courseId}/course-competencies';

    private getBasePath(courseId: number): string {
        return this.resourceUrl.replace('{courseId}', courseId.toString());
    }

    async getCourseCompetenciesByCourseId(courseId: number): Promise<CourseCompetency[]> {
        return this.get<CourseCompetency[]>(this.getBasePath(courseId));
    }

    async getCourseCompetencyById(courseId: number, courseCompetencyId: number): Promise<CourseCompetency> {
        return this.get<CourseCompetency>(`${this.getBasePath(courseId)}/${courseCompetencyId}`);
    }

    async getCourseCompetencyProgressById(courseId: number, courseCompetencyId: number, refresh = false): Promise<CompetencyProgress> {
        let params = new HttpParams();
        params = params.set('refresh', refresh.toString());
        return this.get<CompetencyProgress>(`${this.getBasePath(courseId)}/${courseCompetencyId}/student-progress`, { params: params });
    }

    async getJoL(courseId: number, courseCompetencyId: number): Promise<CompetencyJoLResponse> {
        return this.get<CompetencyJoLResponse>(`${this.getBasePath(courseId)}/${courseCompetencyId}/jol`);
    }
}
