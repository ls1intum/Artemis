import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HTTP_INTERCEPTORS, HttpClient, HttpContext, HttpErrorResponse, HttpRequest, provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService, provideTranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { PostService } from 'app/communication/service/post.service';
import { AnswerPostService } from 'app/communication/service/answer-post.service';
import englishErrors from 'src/main/webapp/i18n/en/error.json';
import { Observable, throwError } from 'rxjs';
import { ErrorHandlerInterceptor, SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { AccountService } from 'app/core/auth/account.service';

describe(`ErrorHandlerInterceptor`, () => {
    let errorHandlerInterceptor: ErrorHandlerInterceptor;

    let eventManagerMock: EventManager;
    let accountServiceMock: AccountService;

    beforeEach(() => {
        eventManagerMock = {
            broadcast: vi.fn(),
        } as any as EventManager;
        accountServiceMock = {
            isAuthenticated: vi.fn(),
        } as any as AccountService;

        TestBed.configureTestingModule({
            providers: [ErrorHandlerInterceptor, { provide: EventManager, useValue: eventManagerMock }, { provide: AccountService, useValue: accountServiceMock }],
        });

        errorHandlerInterceptor = TestBed.inject(ErrorHandlerInterceptor);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it.each([400, 401, 403, 404, 500])('should broadcast http errors', (status: number) => {
        const error = new HttpErrorResponse({ status });
        const mockHandler = {
            handle: () => throwError(() => error),
        };
        vi.spyOn(accountServiceMock, 'isAuthenticated').mockReturnValue(true);

        errorHandlerInterceptor.intercept({} as HttpRequest<any>, mockHandler).subscribe({ error: () => {} });

        expect(eventManagerMock.broadcast).toHaveBeenCalledOnce();
        expect(eventManagerMock.broadcast).toHaveBeenCalledWith({
            name: 'artemisApp.httpError',
            content: error,
        });
    });

    it('should leave the error to a caller that shows it itself', () => {
        const mockHandler = {
            handle: () => throwError(() => new HttpErrorResponse({ status: 400 })),
        };
        vi.spyOn(accountServiceMock, 'isAuthenticated').mockReturnValue(true);
        const request = new HttpRequest('PUT', '/api/lecture/lectures/1/text-units', {}, { context: new HttpContext().set(SKIP_HTTP_ERROR_ALERT, true) });

        errorHandlerInterceptor.intercept(request, mockHandler).subscribe({ error: () => {} });

        expect(eventManagerMock.broadcast).not.toHaveBeenCalled();
    });

    it.each([{ url: '/api/core/public/account' }, { url: '/api/account/basic-information' }])(
        'should not broadcast an http error if status is 401 but url includes /api/core/public/account or /api/account/basic-information',
        ({ url }) => {
            const error = new HttpErrorResponse({ status: 401, url });
            const mockHandler = {
                handle: () => throwError(() => error),
            };
            const isAuthenticatedSpy = vi.spyOn(accountServiceMock, 'isAuthenticated').mockReturnValue(false);

            errorHandlerInterceptor.intercept({} as HttpRequest<any>, mockHandler).subscribe({ error: () => {} });

            expect(isAuthenticatedSpy).toHaveBeenCalledOnce();
        },
    );
});

describe('Forwarded-source HTTP error handling', () => {
    let httpMock: HttpTestingController;
    let alerts: AlertService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(withInterceptorsFromDi()),
                provideHttpClientTesting(),
                provideTranslateService(),
                { provide: HTTP_INTERCEPTORS, useClass: ErrorHandlerInterceptor, multi: true },
                { provide: AccountService, useValue: { isAuthenticated: () => true } },
            ],
        });
        const translations = TestBed.inject(TranslateService);
        translations.setTranslation('en', englishErrors);
        translations.use('en');
        httpMock = TestBed.inject(HttpTestingController);
        alerts = TestBed.inject(AlertService);
    });

    afterEach(() => httpMock.verify());

    it.each([
        { sourceType: 'post', status: 403 },
        { sourceType: 'answer', status: 403 },
        { sourceType: 'post', status: 404 },
        { sourceType: 'answer', status: 404 },
        { sourceType: 'post', status: 500 },
        { sourceType: 'answer', status: 500 },
    ])('leaves an unavailable $sourceType preview ($status) to the chat without a global alert', ({ sourceType, status }) => {
        const request: Observable<unknown> =
            sourceType === 'post'
                ? TestBed.inject(PostService).getSourcePostsByIds(589, [54700, 54706])
                : TestBed.inject(AnswerPostService).getSourceAnswerPostsByIds(589, [54700, 54706]);
        let receivedError: HttpErrorResponse | undefined;
        request.subscribe({ error: (error: HttpErrorResponse) => (receivedError = error) });

        const endpoint = sourceType === 'post' ? 'messages-source-posts' : 'answer-messages-source-posts';
        httpMock
            .expectOne((req) => req.url === `api/communication/courses/589/${endpoint}`)
            .flush({ title: 'Forbidden', message: `error.http.${status}` }, { status, statusText: 'Request failed' });

        expect(receivedError?.status).toBe(status);
        expect(alerts.get()).toHaveLength(0);
    });

    it('uses readable permission guidance when a specific 403 translation is unavailable', () => {
        TestBed.inject(HttpClient)
            .get('/api/communication/courses/589/messages')
            .subscribe({ error: () => {} });
        httpMock.expectOne('/api/communication/courses/589/messages').flush({ title: 'Forbidden', message: 'error.unknownPermission' }, { status: 403, statusText: 'Forbidden' });
        expect(alerts.get()).toHaveLength(1);
        expect(alerts.get()[0].message).toBe('You do not have permission to view the requested content. Contact your course team if you need access.');
    });

    it.each([
        { method: 'GET', expected: 'You do not have permission to view the requested content. Contact your course team if you need access.' },
        { method: 'POST', expected: 'You do not have permission to perform this action. Contact your course team if you need access.' },
    ])('still explains a forbidden ordinary $method request', ({ method, expected }) => {
        TestBed.inject(HttpClient)
            .request(method, '/api/communication/courses/589/messages')
            .subscribe({ error: () => {} });
        httpMock.expectOne('/api/communication/courses/589/messages').flush({ title: 'Forbidden', message: 'error.http.403' }, { status: 403, statusText: 'Forbidden' });
        expect(alerts.get()).toHaveLength(1);
        expect(alerts.get()[0].message).toBe(expected);
    });
});
