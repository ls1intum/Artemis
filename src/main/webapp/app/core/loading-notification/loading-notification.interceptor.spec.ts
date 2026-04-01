import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpHandler, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, of, throwError } from 'rxjs';
import { TestBed } from '@angular/core/testing';
import { LoadingNotificationInterceptor } from 'app/core/loading-notification/loading-notification.interceptor';
import { LoadingNotificationService } from 'app/core/loading-notification/loading-notification.service';

describe('LoadingNotificationInterceptor', () => {
    setupTestBed({ zoneless: true });

    let interceptor: LoadingNotificationInterceptor;
    let loadingNotificationServiceMock: LoadingNotificationService;

    beforeEach(() => {
        loadingNotificationServiceMock = {
            startLoading: vi.fn(),
            stopLoading: vi.fn(),
        } as any as LoadingNotificationService;

        TestBed.configureTestingModule({
            providers: [LoadingNotificationInterceptor, { provide: LoadingNotificationService, useValue: loadingNotificationServiceMock }],
        });

        interceptor = TestBed.inject(LoadingNotificationInterceptor);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        // Reset activeRequests for each test
        interceptor.activeRequests = 0;
    });

    describe('initialization', () => {
        it('should be created', () => {
            expect(interceptor).toBeTruthy();
        });

        it('should have activeRequests initialized to 0', () => {
            expect(interceptor.activeRequests).toBe(0);
        });
    });

    describe('intercept - single request', () => {
        it('should call startLoading when first request is made', () => {
            const request = new HttpRequest('GET', '/api/test');
            const mockHandler: HttpHandler = {
                handle: () => of(new HttpResponse({ status: 200 })),
            };

            interceptor.intercept(request, mockHandler).subscribe();

            expect(loadingNotificationServiceMock.startLoading).toHaveBeenCalledOnce();
        });

        it('should increment and decrement activeRequests correctly during request lifecycle', () => {
            const request = new HttpRequest('GET', '/api/test');
            let completeRequest: () => void = () => {};

            const mockHandler: HttpHandler = {
                handle: () =>
                    new Observable((observer) => {
                        completeRequest = () => {
                            observer.next(new HttpResponse({ status: 200 }));
                            observer.complete();
                        };
                    }),
            };

            interceptor.intercept(request, mockHandler).subscribe();
            // While request is in progress, activeRequests should be 1
            expect(interceptor.activeRequests).toBe(1);

            // Complete the request
            completeRequest();
            // After completion, activeRequests should be back to 0
            expect(interceptor.activeRequests).toBe(0);
        });

        it('should call stopLoading when the only active request completes', () => {
            const request = new HttpRequest('GET', '/api/test');
            const mockHandler: HttpHandler = {
                handle: () => of(new HttpResponse({ status: 200 })),
            };

            interceptor.intercept(request, mockHandler).subscribe();

            expect(loadingNotificationServiceMock.stopLoading).toHaveBeenCalledOnce();
        });

        it('should call stopLoading when request errors', () => {
            const request = new HttpRequest('GET', '/api/test');
            const mockHandler: HttpHandler = {
                handle: () => throwError(() => new Error('Network error')),
            };

            interceptor.intercept(request, mockHandler).subscribe({
                error: () => {
                    // Expected error
                },
            });

            expect(loadingNotificationServiceMock.stopLoading).toHaveBeenCalledOnce();
        });
    });

    describe('intercept - multiple concurrent requests', () => {
        it('should only call startLoading once for multiple concurrent requests', () => {
            const request1 = new HttpRequest('GET', '/api/test1');
            const request2 = new HttpRequest('GET', '/api/test2');

            const mockHandler1: HttpHandler = {
                handle: () =>
                    new Observable(() => {
                        // Request stays pending
                    }),
            };
            const mockHandler2: HttpHandler = {
                handle: () =>
                    new Observable(() => {
                        // Request stays pending
                    }),
            };

            interceptor.intercept(request1, mockHandler1).subscribe();
            interceptor.intercept(request2, mockHandler2).subscribe();

            // startLoading should only be called once (on first request)
            expect(loadingNotificationServiceMock.startLoading).toHaveBeenCalledOnce();
        });

        it('should not call stopLoading until all requests complete', () => {
            const request1 = new HttpRequest('GET', '/api/test1');
            const request2 = new HttpRequest('GET', '/api/test2');
            let completeFirstRequest: () => void = () => {};
            let completeSecondRequest: () => void = () => {};

            const mockHandler1: HttpHandler = {
                handle: () =>
                    new Observable((observer) => {
                        completeFirstRequest = () => {
                            observer.next(new HttpResponse({ status: 200 }));
                            observer.complete();
                        };
                    }),
            };
            const mockHandler2: HttpHandler = {
                handle: () =>
                    new Observable((observer) => {
                        completeSecondRequest = () => {
                            observer.next(new HttpResponse({ status: 200 }));
                            observer.complete();
                        };
                    }),
            };

            interceptor.intercept(request1, mockHandler1).subscribe();
            interceptor.intercept(request2, mockHandler2).subscribe();

            // Complete first request
            completeFirstRequest();
            expect(loadingNotificationServiceMock.stopLoading).not.toHaveBeenCalled();

            // Complete second request
            completeSecondRequest();
            expect(loadingNotificationServiceMock.stopLoading).toHaveBeenCalledOnce();
        });

        it('should track activeRequests correctly with multiple requests', () => {
            const request1 = new HttpRequest('GET', '/api/test1');
            const request2 = new HttpRequest('GET', '/api/test2');
            let completeFirstRequest: () => void = () => {};
            let completeSecondRequest: () => void = () => {};

            const mockHandler1: HttpHandler = {
                handle: () =>
                    new Observable((observer) => {
                        completeFirstRequest = () => {
                            observer.next(new HttpResponse({ status: 200 }));
                            observer.complete();
                        };
                    }),
            };
            const mockHandler2: HttpHandler = {
                handle: () =>
                    new Observable((observer) => {
                        completeSecondRequest = () => {
                            observer.next(new HttpResponse({ status: 200 }));
                            observer.complete();
                        };
                    }),
            };

            interceptor.intercept(request1, mockHandler1).subscribe();
            expect(interceptor.activeRequests).toBe(1);

            interceptor.intercept(request2, mockHandler2).subscribe();
            expect(interceptor.activeRequests).toBe(2);

            completeFirstRequest();
            expect(interceptor.activeRequests).toBe(1);

            completeSecondRequest();
            expect(interceptor.activeRequests).toBe(0);
        });
    });

    describe('intercept - sequential requests', () => {
        it('should call startLoading again after all requests complete', () => {
            const request1 = new HttpRequest('GET', '/api/test1');
            const request2 = new HttpRequest('GET', '/api/test2');
            const mockHandler: HttpHandler = {
                handle: () => of(new HttpResponse({ status: 200 })),
            };

            // First request completes
            interceptor.intercept(request1, mockHandler).subscribe();

            // Second request starts after first completes
            interceptor.intercept(request2, mockHandler).subscribe();

            expect(loadingNotificationServiceMock.startLoading).toHaveBeenCalledTimes(2);
            expect(loadingNotificationServiceMock.stopLoading).toHaveBeenCalledTimes(2);
        });
    });

    describe('intercept - request types', () => {
        it('should handle POST requests', () => {
            const request = new HttpRequest('POST', '/api/test', { data: 'test' });
            const mockHandler: HttpHandler = {
                handle: () => of(new HttpResponse({ status: 201 })),
            };

            interceptor.intercept(request, mockHandler).subscribe();

            expect(loadingNotificationServiceMock.startLoading).toHaveBeenCalledOnce();
            expect(loadingNotificationServiceMock.stopLoading).toHaveBeenCalledOnce();
        });

        it('should handle PUT requests', () => {
            const request = new HttpRequest('PUT', '/api/test/1', { data: 'updated' });
            const mockHandler: HttpHandler = {
                handle: () => of(new HttpResponse({ status: 200 })),
            };

            interceptor.intercept(request, mockHandler).subscribe();

            expect(loadingNotificationServiceMock.startLoading).toHaveBeenCalledOnce();
            expect(loadingNotificationServiceMock.stopLoading).toHaveBeenCalledOnce();
        });

        it('should handle DELETE requests', () => {
            const request = new HttpRequest('DELETE', '/api/test/1');
            const mockHandler: HttpHandler = {
                handle: () => of(new HttpResponse({ status: 204 })),
            };

            interceptor.intercept(request, mockHandler).subscribe();

            expect(loadingNotificationServiceMock.startLoading).toHaveBeenCalledOnce();
            expect(loadingNotificationServiceMock.stopLoading).toHaveBeenCalledOnce();
        });
    });

    describe('intercept - response passthrough', () => {
        it('should pass through the response from the handler', async () => {
            const request = new HttpRequest('GET', '/api/test');
            const expectedResponse = new HttpResponse({ status: 200, body: { message: 'success' } });
            const mockHandler: HttpHandler = {
                handle: () => of(expectedResponse),
            };

            return new Promise<void>((resolve) => {
                interceptor.intercept(request, mockHandler).subscribe((response) => {
                    expect(response).toBe(expectedResponse);
                    resolve();
                });
            });
        });

        it('should pass through errors from the handler', async () => {
            const request = new HttpRequest('GET', '/api/test');
            const expectedError = new Error('Network error');
            const mockHandler: HttpHandler = {
                handle: () => throwError(() => expectedError),
            };

            return new Promise<void>((resolve) => {
                interceptor.intercept(request, mockHandler).subscribe({
                    error: (error) => {
                        expect(error).toBe(expectedError);
                        resolve();
                    },
                });
            });
        });
    });

    describe('intercept - file and PDF requests', () => {
        it('should not track PDF file requests', () => {
            const request = new HttpRequest('GET', '/api/core/files/courses/1/attachments/123');
            const mockHandler: HttpHandler = {
                handle: () => of(new HttpResponse({ status: 200 })),
            };

            interceptor.intercept(request, mockHandler).subscribe();

            expect(loadingNotificationServiceMock.startLoading).not.toHaveBeenCalled();
            expect(interceptor.activeRequests).toBe(0);
        });

        it('should not track attachment requests', () => {
            const request = new HttpRequest('GET', '/api/lecture/files/attachments/attachment-unit/456/file.pdf');
            const mockHandler: HttpHandler = {
                handle: () => of(new HttpResponse({ status: 200 })),
            };

            interceptor.intercept(request, mockHandler).subscribe();

            expect(loadingNotificationServiceMock.startLoading).not.toHaveBeenCalled();
            expect(interceptor.activeRequests).toBe(0);
        });

        it('should not track blob requests', () => {
            const request = new HttpRequest('GET', '/api/some-endpoint', { responseType: 'blob' });
            const mockHandler: HttpHandler = {
                handle: () => of(new HttpResponse({ status: 200 })),
            };

            interceptor.intercept(request, mockHandler).subscribe();

            expect(loadingNotificationServiceMock.startLoading).not.toHaveBeenCalled();
            expect(interceptor.activeRequests).toBe(0);
        });

        it('should not track arraybuffer requests', () => {
            const request = new HttpRequest('GET', '/api/some-endpoint', { responseType: 'arraybuffer' });
            const mockHandler: HttpHandler = {
                handle: () => of(new HttpResponse({ status: 200 })),
            };

            interceptor.intercept(request, mockHandler).subscribe();

            expect(loadingNotificationServiceMock.startLoading).not.toHaveBeenCalled();
            expect(interceptor.activeRequests).toBe(0);
        });

        it('should still track regular API requests after file request detection is added', () => {
            const request = new HttpRequest('GET', '/api/courses/1');
            const mockHandler: HttpHandler = {
                handle: () => of(new HttpResponse({ status: 200 })),
            };

            interceptor.intercept(request, mockHandler).subscribe();

            expect(loadingNotificationServiceMock.startLoading).toHaveBeenCalledOnce();
            expect(loadingNotificationServiceMock.stopLoading).toHaveBeenCalledOnce();
            expect(interceptor.activeRequests).toBe(0);
        });

        it('should not affect activeRequests counter when mixing file and non-file requests', () => {
            const regularRequest = new HttpRequest('GET', '/api/courses/1');
            const fileRequest = new HttpRequest('GET', '/api/core/files/courses/1/attachments/123');
            let completeRegularRequest: () => void = () => {};

            const mockHandler1: HttpHandler = {
                handle: () =>
                    new Observable((observer) => {
                        completeRegularRequest = () => {
                            observer.next(new HttpResponse({ status: 200 }));
                            observer.complete();
                        };
                    }),
            };
            const mockHandler2: HttpHandler = {
                handle: () => of(new HttpResponse({ status: 200 })),
            };

            // Start regular request
            interceptor.intercept(regularRequest, mockHandler1).subscribe();
            expect(interceptor.activeRequests).toBe(1);

            // File request should not increment counter
            interceptor.intercept(fileRequest, mockHandler2).subscribe();
            expect(interceptor.activeRequests).toBe(1);

            // Complete regular request
            completeRegularRequest();
            expect(interceptor.activeRequests).toBe(0);
            expect(loadingNotificationServiceMock.stopLoading).toHaveBeenCalledOnce();
        });

        it('should handle file request errors without affecting activeRequests', () => {
            const fileRequest = new HttpRequest('GET', '/api/core/files/courses/1/attachments/123');
            const mockHandler: HttpHandler = {
                handle: () => throwError(() => new Error('File not found')),
            };

            interceptor.intercept(fileRequest, mockHandler).subscribe({
                error: () => {
                    // Expected error
                },
            });

            expect(loadingNotificationServiceMock.startLoading).not.toHaveBeenCalled();
            expect(loadingNotificationServiceMock.stopLoading).not.toHaveBeenCalled();
            expect(interceptor.activeRequests).toBe(0);
        });

        it('should detect file requests by /files/ in URL', () => {
            const request = new HttpRequest('GET', '/api/public/files/lectures/123/document.pdf');
            const mockHandler: HttpHandler = {
                handle: () => of(new HttpResponse({ status: 200 })),
            };

            interceptor.intercept(request, mockHandler).subscribe();

            expect(loadingNotificationServiceMock.startLoading).not.toHaveBeenCalled();
            expect(interceptor.activeRequests).toBe(0);
        });

        it('should detect file requests by /attachments/ in URL', () => {
            const request = new HttpRequest('GET', '/api/attachments/789/download');
            const mockHandler: HttpHandler = {
                handle: () => of(new HttpResponse({ status: 200 })),
            };

            interceptor.intercept(request, mockHandler).subscribe();

            expect(loadingNotificationServiceMock.startLoading).not.toHaveBeenCalled();
            expect(interceptor.activeRequests).toBe(0);
        });
    });
});
