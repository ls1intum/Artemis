import { EntityNotFoundError } from 'app/course/learning-paths/exceptions/entity-not-found.error';
import { HttpMethod } from 'app/admin/metrics/metrics.model';
import { inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';

export abstract class BaseApiHttpService {
    private readonly httpClient = inject(HttpClient);

    private readonly baseUrl = 'api';

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
                this.httpClient.request<T>(method, `${this.baseUrl}/${url}`, {
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

    protected async get<T>(
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

    protected async post<T>(
        url: string,
        body?: any,
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
        return await this.request<T>(HttpMethod.Post, url, { body: body, ...options });
    }
}
