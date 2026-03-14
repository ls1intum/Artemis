import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpRequest } from '@angular/common/http';
import { throwError } from 'rxjs';
import { ErrorHandlerInterceptor } from 'app/core/interceptor/errorhandler.interceptor';
import { EventManager } from 'app/shared/service/event-manager.service';
import { AccountService } from 'app/core/auth/account.service';

describe(`ErrorHandlerInterceptor`, () => {
    setupTestBed({ zoneless: true });

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

        errorHandlerInterceptor.intercept({} as HttpRequest<any>, mockHandler).subscribe();

        expect(eventManagerMock.broadcast).toHaveBeenCalledOnce();
        expect(eventManagerMock.broadcast).toHaveBeenCalledWith({
            name: 'artemisApp.httpError',
            content: error,
        });
    });

    it.each([{ url: '/api/core/public/account' }, { url: '/api/core/account' }])(
        'should not broadcast an http error if status is 401 but url includes /api/core/public/account or /api/core/account',
        ({ url }) => {
            const error = new HttpErrorResponse({ status: 401, url });
            const mockHandler = {
                handle: () => throwError(() => error),
            };
            const isAuthenticatedSpy = vi.spyOn(accountServiceMock, 'isAuthenticated').mockReturnValue(false);

            errorHandlerInterceptor.intercept({} as HttpRequest<any>, mockHandler).subscribe();

            expect(isAuthenticatedSpy).toHaveBeenCalledOnce();
        },
    );
});
