import { EntityNotFoundError } from 'app/course/learning-paths/exceptions/entity-not-found.error';
import { HttpMethod } from 'app/admin/metrics/metrics.model';
import { inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams, HttpResponse } from '@angular/common/http';
import { lastValueFrom } from 'rxjs';

export abstract class BaseApiHttpService {
    private readonly httpClient: HttpClient = inject(HttpClient);

    private readonly baseUrl = 'api';

    /**
     * Debounces a function call to prevent it from being called multiple times in a short period.
     * @param callback The function to debounce.
     * @param delay The delay in milliseconds to wait before calling the function.
     */
    public static debounce<T extends unknown[]>(callback: (...args: T) => void, delay: number) {
        let timer: NodeJS.Timeout | undefined;
        return function (...args: T) {
            if (timer) {
                clearTimeout(timer);
            }
            timer = setTimeout(() => {
                callback(...args);
            }, delay);
        };
    }

    /**
     * Handles the error response from the server and throws specific errors
     * which can be handled on service or component level.
     *
     * @param error
     * @private
     */
    private handleHttpError(error: HttpResponse<any>): void {
        const statusCode = error.status;
        if (statusCode == 404) {
            throw new EntityNotFoundError();
        }
    }

    /**
     * Constructs a request which interprets the body as a JavaScript object and returns
     * the response body as a Promise in the requested type.
     *
     * @param method  The HTTP method.
     * @param url     The endpoint URL excluding the base server url (/api).
     * @param options The HTTP options to send with the request.
     *
     * @return  An `Promise` of the response body of type `T`.
     */
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
    ): Promise<T> {
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

    /**
     * Constructs a `GET` request that interprets the body as JSON and
     * returns a Promise of an object of type `T`.
     *
     * @param url     The endpoint URL excluding the base server url (/api).
     * @param options The HTTP options to send with the request.
     * @protected
     *
     * @return An `Promise` of type `Object` (T),
     */
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

    /**
     * Constructs a `POST` request that interprets the body as JSON and
     * returns a Promise of an object of type `T`.
     *
     * @param url The endpoint URL excluding the base server url (/api).
     * @param body The content to include in the body of the request.
     * @param options The HTTP options to send with the request.
     * @protected
     *
     * @return An `Promise` of type `Object` (T),
     */
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

    /**
     * Constructs a `DELETE` request that interprets the body as JSON and
     * returns a Promise of an object of type `T`.
     *
     * @param url The endpoint URL excluding the base server url (/api).
     * @param options The HTTP options to send with the request.
     * @protected
     *
     * @return An `Promise` of type `Object` (T),
     */
    protected async delete<T>(
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
        return await this.request<T>(HttpMethod.Delete, url, options);
    }
}
