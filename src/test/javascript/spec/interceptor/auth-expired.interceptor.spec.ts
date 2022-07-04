import { HttpErrorResponse, HttpRequest } from '@angular/common/http';
import { AuthExpiredInterceptor } from 'app/core/interceptor/auth-expired.interceptor';
import { LoginService } from 'app/core/login/login.service';
import { StateStorageService } from 'app/core/auth/state-storage.service';
import { Router } from '@angular/router';
import { AuthServerProvider } from 'app/core/auth/auth-jwt.service';
import { throwError } from 'rxjs';

describe(`AuthExpiredInterceptor`, () => {
    let authInterceptor: AuthExpiredInterceptor;

    let loginServiceMock: LoginService;
    let stateStorageServiceMock: StateStorageService;
    let authServerProviderMock: AuthServerProvider;

    const token = 'token-123';

    const routerMock = {
        routerState: {
            snapshot: {
                url: 'https://example.com',
            },
        },
    } as any as Router;

    beforeEach(() => {
        loginServiceMock = {
            logout: jest.fn(),
        } as any as LoginService;
        stateStorageServiceMock = {
            storeUrl: jest.fn(),
        } as any as StateStorageService;
        authServerProviderMock = {
            getToken: jest.fn(),
        } as any as AuthServerProvider;

        authInterceptor = new AuthExpiredInterceptor(loginServiceMock, stateStorageServiceMock, routerMock, authServerProviderMock);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should logout and store the current url if status is 401 and there is no token', () => {
        const mockHandler = {
            handle: () => throwError(() => new HttpErrorResponse({ status: 401 })),
        };
        const tokenSpy = jest.spyOn(authServerProviderMock, 'getToken').mockReturnValue(token);

        authInterceptor.intercept({} as HttpRequest<any>, mockHandler).subscribe();

        expect(tokenSpy).toHaveBeenCalledOnce();
        expect(loginServiceMock.logout).toHaveBeenCalledWith(false);
        expect(stateStorageServiceMock.storeUrl).toHaveBeenCalledWith('https://example.com');
    });

    it('should ignore if there is no token provided', () => {
        const mockHandler = {
            handle: () => throwError(() => new HttpErrorResponse({ status: 401 })),
        };
        const tokenSpy = jest.spyOn(authServerProviderMock, 'getToken');

        authInterceptor.intercept({} as HttpRequest<any>, mockHandler).subscribe();

        expect(tokenSpy).toHaveBeenCalledOnce();
        expect(loginServiceMock.logout).toHaveBeenCalledTimes(0);
        expect(stateStorageServiceMock.storeUrl).toHaveBeenCalledTimes(0);
    });

    it('should ignore if the error is anything other than 401', () => {
        const mockHandler = {
            handle: () => throwError(() => new HttpErrorResponse({ status: 400 })),
        };
        const tokenSpy = jest.spyOn(authServerProviderMock, 'getToken').mockReturnValue(token);

        authInterceptor.intercept({} as HttpRequest<any>, mockHandler).subscribe();

        expect(tokenSpy).toHaveBeenCalledTimes(0);
        expect(loginServiceMock.logout).toHaveBeenCalledTimes(0);
        expect(stateStorageServiceMock.storeUrl).toHaveBeenCalledTimes(0);
    });
});
