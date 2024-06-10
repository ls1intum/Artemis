import { Injectable, inject } from '@angular/core';
import { LearningObjectType, LearningPathNavigationDto, LearningPathNavigationOverviewDto } from 'app/entities/competency/learning-path.model';
import { EntityNotFoundError } from 'app/course/learning-paths/exceptions/entity-not-found.error';
import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';
import { HttpMethod } from 'app/admin/metrics/metrics.model';

@Injectable({
    providedIn: 'root',
})
export class LearningPathApiService {
    private readonly resourceURL = 'api';

    private readonly httpClient = inject(HttpClient);

    private handleHttpError(error: HttpResponse<any>): void {
        const statusCode = error.status;
        if (statusCode == 404) {
            throw new EntityNotFoundError();
        }
    }

    private async request<T>(
        method: HttpMethod,
        url: string,
        options?: {
            body?: any;
            headers?:
                | HttpHeaders
                | {
                      [header: string]: string | string[];
                  };
            params?:
                | HttpParams
                | {
                      [param: string]: string | number | boolean | ReadonlyArray<string | number | boolean>;
                  };
            responseType?: 'json';
        },
    ) {
        try {
            const response = await lastValueFrom(
                this.httpClient.request<T>(method, url, {
                    observe: 'response',
                    ...options,
                }),
            );
            return response.body!;
        } catch (error) {
            this.handleHttpError(error);
            throw Error('Internal server error');
        }
    }

    private async get<T>(
        url: string,
        options?: {
            headers?:
                | HttpHeaders
                | {
                      [header: string]: string | string[];
                  };
            params?:
                | HttpParams
                | {
                      [param: string]: string | number | boolean | ReadonlyArray<string | number | boolean>;
                  };
        },
    ): Promise<T> {
        return await this.request<T>(HttpMethod.Get, url, options);
    }

    private async post<T>(url: string, body?: any): Promise<T> {
        return await this.request<T>(HttpMethod.Post, url, { body: body });
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
        return await this.get<LearningPathNavigationDto>(`${this.resourceURL}/learning-path/${learningPathId}/navigation`, { params: params });
    }

    async generateLearningPath(courseId: number): Promise<number> {
        return await this.post<number>(`${this.resourceURL}/courses/${courseId}/learning-path`);
    }

    async getLearningPathNavigationOverview(learningPathId: number): Promise<LearningPathNavigationOverviewDto> {
        return await this.get<LearningPathNavigationOverviewDto>(`${this.resourceURL}/learning-path/${learningPathId}/navigation-overview`);
    }
}
