import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpRequest } from '@angular/common/http';
import { AuthExpiredInterceptor } from 'app/core/interceptor/auth-expired.interceptor';
import { LoginService } from 'app/core/login/login.service';
import { AccountService } from 'app/core/auth/account.service';
import { StateStorageService } from 'app/core/auth/state-storage.service';
import { Router } from '@angular/router';
import { throwError } from 'rxjs';
import { ArtemisTestModule } from '../test.module';

describe('AuthExpiredInterceptor', () => {
    let authInterceptor: AuthExpiredInterceptor;

    let loginServiceMock: LoginService;
    let stateStorageServiceMock: StateStorageService;
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
            logout: jest.fn(),
        } as any as LoginService;
        stateStorageServiceMock = {
            storeUrl: jest.fn(),
        } as any as StateStorageService;
        accountServiceMock = {
            isAuthenticated: jest.fn(),
        } as any as AccountService;

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [
                AuthExpiredInterceptor,
                { provide: LoginService, useValue: loginServiceMock },
                { provide: StateStorageService, useValue: stateStorageServiceMock },
                { provide: AccountService, useValue: accountServiceMock },
                { provide: Router, useValue: routerMock },
            ],
        });

        authInterceptor = TestBed.inject(AuthExpiredInterceptor);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should logout and store the current url if status is 401 and user is authenticated', () => {
        const mockHandler = {
            handle: () => throwError(() => new HttpErrorResponse({ status: 401 })),
        };
        const isAuthenticatedSpy = jest.spyOn(accountServiceMock, 'isAuthenticated').mockReturnValue(true);

        authInterceptor.intercept({} as HttpRequest<any>, mockHandler).subscribe();

        expect(isAuthenticatedSpy).toHaveBeenCalledOnce();
        expect(loginServiceMock.logout).toHaveBeenCalledWith(false);
        expect(stateStorageServiceMock.storeUrl).toHaveBeenCalledWith('https://example.com');
    });

    it('should ignore if user is not authenticated', () => {
        const mockHandler = {
            handle: () => throwError(() => new HttpErrorResponse({ status: 401 })),
        };
        const isAuthenticatedSpy = jest.spyOn(accountServiceMock, 'isAuthenticated').mockReturnValue(false);

        authInterceptor.intercept({} as HttpRequest<any>, mockHandler).subscribe();

        expect(isAuthenticatedSpy).toHaveBeenCalledOnce();
        expect(loginServiceMock.logout).not.toHaveBeenCalled();
        expect(stateStorageServiceMock.storeUrl).not.toHaveBeenCalled();
    });

    it('should ignore if the error is anything other than 401', () => {
        const mockHandler = {
            handle: () => throwError(() => new HttpErrorResponse({ status: 400 })),
        };
        const isAuthenticatedSpy = jest.spyOn(accountServiceMock, 'isAuthenticated').mockReturnValue(true);

        authInterceptor.intercept({} as HttpRequest<any>, mockHandler).subscribe();

        expect(isAuthenticatedSpy).not.toHaveBeenCalled();
        expect(loginServiceMock.logout).not.toHaveBeenCalled();
        expect(stateStorageServiceMock.storeUrl).not.toHaveBeenCalled();
    });
});
