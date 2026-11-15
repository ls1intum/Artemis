import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpRequest } from '@angular/common/http';
import { AuthExpiredInterceptor } from 'app/core/interceptor/auth-expired.interceptor';
import { LoginService } from 'app/core/login/login.service';
import { AccountService } from 'app/core/auth/account.service';
import { Router } from '@angular/router';
import { throwError } from 'rxjs';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';

describe('AuthExpiredInterceptor', () => {
    let authInterceptor: AuthExpiredInterceptor;

    let loginServiceMock: LoginService;
    let sessionStorageServiceMock: SessionStorageService;
    let accountServiceMock: AccountService;

    const routerMock = {
        routerState: {
            snapshot: {
                url: 'https://example.com',
            },
        },
    } as any as Router;

    beforeEach(() => {
        loginServiceMock = {
            logout: vi.fn(),
            logoutWithServerSession: vi.fn(),
        } as any as LoginService;
        sessionStorageServiceMock = {
            store: vi.fn(),
        } as any as SessionStorageService;
        accountServiceMock = {
            isAuthenticated: vi.fn(),
        } as any as AccountService;

        TestBed.configureTestingModule({
            providers: [
                AuthExpiredInterceptor,
                { provide: LoginService, useValue: loginServiceMock },
                { provide: SessionStorageService, useValue: sessionStorageServiceMock },
                { provide: AccountService, useValue: accountServiceMock },
                { provide: Router, useValue: routerMock },
            ],
        });

        authInterceptor = TestBed.inject(AuthExpiredInterceptor);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should logout and store the current url if status is 401 and user is authenticated', () => {
        const mockHandler = {
            handle: () => throwError(() => new HttpErrorResponse({ status: 401 })),
        };
        const isAuthenticatedSpy = vi.spyOn(accountServiceMock, 'isAuthenticated').mockReturnValue(true);

        authInterceptor.intercept({} as HttpRequest<any>, mockHandler).subscribe({ error: () => {} });

        expect(isAuthenticatedSpy).toHaveBeenCalledOnce();
        expect(loginServiceMock.logout).toHaveBeenCalledWith(false);
        expect(sessionStorageServiceMock.store).toHaveBeenCalledWith('previousUrl', 'https://example.com');
    });

    it('should end the server session and hand the current url over if the session is outdated', () => {
        const mockHandler = {
            handle: () => throwError(() => new HttpErrorResponse({ status: 401, error: { errorKey: 'sessionOutdated' } })),
        };
        vi.spyOn(accountServiceMock, 'isAuthenticated').mockReturnValue(true);

        authInterceptor.intercept({} as HttpRequest<any>, mockHandler).subscribe({ error: () => {} });

        // the login service stores the url itself, after the server logout cleared the session storage
        expect(loginServiceMock.logoutWithServerSession).toHaveBeenCalledExactlyOnceWith('https://example.com');
        expect(loginServiceMock.logout).not.toHaveBeenCalled();
        expect(sessionStorageServiceMock.store).not.toHaveBeenCalled();
    });

    it('should keep the client-only logout for a 401 with another error key', () => {
        const mockHandler = {
            handle: () => throwError(() => new HttpErrorResponse({ status: 401, error: { errorKey: 'somethingElse' } })),
        };
        vi.spyOn(accountServiceMock, 'isAuthenticated').mockReturnValue(true);

        authInterceptor.intercept({} as HttpRequest<any>, mockHandler).subscribe({ error: () => {} });

        expect(loginServiceMock.logout).toHaveBeenCalledWith(false);
        expect(loginServiceMock.logoutWithServerSession).not.toHaveBeenCalled();
    });

    it('should ignore if user is not authenticated', () => {
        const mockHandler = {
            handle: () => throwError(() => new HttpErrorResponse({ status: 401 })),
        };
        const isAuthenticatedSpy = vi.spyOn(accountServiceMock, 'isAuthenticated').mockReturnValue(false);

        authInterceptor.intercept({} as HttpRequest<any>, mockHandler).subscribe({ error: () => {} });

        expect(isAuthenticatedSpy).toHaveBeenCalledOnce();
        expect(loginServiceMock.logout).not.toHaveBeenCalled();
        expect(sessionStorageServiceMock.store).not.toHaveBeenCalled();
    });

    it('should ignore if the error is anything other than 401', () => {
        const mockHandler = {
            handle: () => throwError(() => new HttpErrorResponse({ status: 400 })),
        };
        const isAuthenticatedSpy = vi.spyOn(accountServiceMock, 'isAuthenticated').mockReturnValue(true);

        authInterceptor.intercept({} as HttpRequest<any>, mockHandler).subscribe({ error: () => {} });

        expect(isAuthenticatedSpy).not.toHaveBeenCalled();
        expect(loginServiceMock.logout).not.toHaveBeenCalled();
        expect(sessionStorageServiceMock.store).not.toHaveBeenCalled();
    });
});
