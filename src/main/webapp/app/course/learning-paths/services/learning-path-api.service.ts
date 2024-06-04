import { Injectable, inject } from '@angular/core';
import { LearningObjectType, LearningPathNavigationDto, LearningPathNavigationOverviewDto } from 'app/entities/competency/learning-path.model';
import { EntityNotFoundError } from 'app/course/learning-paths/exceptions/entity-not-found.error';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';

@Injectable({
    providedIn: 'root',
})
export class LearningPathApiService {
    private readonly resourceURL = 'api';

    private readonly httpClient = inject(HttpClient);

    private handleHttpError(error: HttpResponse<any>): void {
        const statusCode = error.status;
        if (statusCode === 404) {
            throw new EntityNotFoundError();
        }
    }

    private async get<T>(url: string, params?: HttpParams): Promise<T> {
        const response = await lastValueFrom(this.httpClient.get<T>(url, { observe: 'response', params: params }));
        if (!response.ok) {
            this.handleHttpError(response);
        }
        return response.body!;
    }

    private async post<T>(url: string, body: any): Promise<T> {
        const response = await lastValueFrom(this.httpClient.post<T>(url, body, { observe: 'response' }));
        if (!response.ok) {
            this.handleHttpError(response);
        }
        return response.body!;
    }

    async getLearningPathId(courseId: number): Promise<number> {
        return await this.get<number>(`${this.resourceURL}/courses/${courseId}/learning-path-id`);
    }

    async getLearningPathNavigation(
        learningPathId: number,
        learningObjectId: number | undefined,
        learningObjectType: LearningObjectType | undefined,
    ): Promise<LearningPathNavigationDto> {
        let params = new HttpParams();
        if (learningObjectId && learningObjectType) {
            params = params.set('learningObjectId', learningObjectId.toString());
            params = params.set('learningObjectType', learningObjectType);
        }
        return await this.get<LearningPathNavigationDto>(`${this.resourceURL}/learning-path/${learningPathId}/navigation`, params);
    }

    async generateLearningPath(courseId: number): Promise<number> {
        return await this.post<number>(`${this.resourceURL}/courses/${courseId}/learning-path`, null);
    }

    async getLearningPathNavigationOverview(learningPathId: number): Promise<LearningPathNavigationOverviewDto> {
        return await this.get<LearningPathNavigationOverviewDto>(`${this.resourceURL}/learning-path/${learningPathId}/navigation-overview`);
    }
}
