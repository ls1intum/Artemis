import { of, throwError } from 'rxjs';
import { MockWebsocketService } from '../helpers/mocks/service/mock-websocket.service';
import { MockRouter } from '../helpers/mocks/mock-router';
import { MockAccountService } from '../helpers/mocks/service/mock-account.service';
import { MockAuthServerProviderService } from '../helpers/mocks/service/mock-auth-server-provider.service';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { LoginService } from 'app/core/login/login.service';
import { AuthServerProvider } from 'app/core/auth/auth-jwt.service';
import { MockNotificationService } from '../helpers/mocks/service/mock-notification.service';
import { TestBed } from '@angular/core/testing';
import { AlertService } from 'app/core/util/alert.service';
import { NotificationService } from 'app/shared/notification/notification.service';
import { Router } from '@angular/router';
import { MockProvider } from 'ng-mocks';

describe('LoginService', () => {
    let accountService: AccountService;
    let authServerProvider: AuthServerProvider;
    let router: Router;
    let alertService: AlertService;
    let notificationService: NotificationService;
    let loginService: LoginService;

    let removeAuthTokenFromCachesStub: jest.SpyInstance;
    let authenticateStub: jest.SpyInstance;
    let alertServiceClearStub: jest.SpyInstance;
    let notificationServiceCleanUpStub: jest.SpyInstance;
    let navigateByUrlStub: jest.SpyInstance;
    let alertServiceErrorStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: JhiWebsocketService, useClass: MockWebsocketService },
                { provide: AuthServerProvider, useClass: MockAuthServerProviderService },
                { provide: Router, useClass: MockRouter },
                MockProvider(AlertService),
                { provide: NotificationService, useClass: MockNotificationService },
            ],
        })
            .compileComponents()
            .then(() => {
                accountService = TestBed.inject(AccountService);
                authServerProvider = TestBed.inject(AuthServerProvider);
                router = TestBed.inject(Router);
                alertService = TestBed.inject(AlertService);
                notificationService = TestBed.inject(NotificationService);
                loginService = TestBed.inject(LoginService);

                removeAuthTokenFromCachesStub = jest.spyOn(authServerProvider, 'removeAuthTokenFromCaches');
                authenticateStub = jest.spyOn(accountService, 'authenticate');
                alertServiceClearStub = jest.spyOn(alertService, 'clear');
                notificationServiceCleanUpStub = jest.spyOn(notificationService, 'cleanUp');
                navigateByUrlStub = jest.spyOn(router, 'navigateByUrl');
                alertServiceErrorStub = jest.spyOn(alertService, 'error');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should properly log out when every action is successful', () => {
        removeAuthTokenFromCachesStub.mockReturnValue(of(undefined));
        navigateByUrlStub.mockReturnValue(Promise.resolve(true));
        loginService.logout(true);

        commonExpects();
        expect(alertServiceErrorStub).not.toHaveBeenCalled();
    });

    it('should emit an error when an action fails', () => {
        const error = 'fatal error';
        removeAuthTokenFromCachesStub.mockReturnValue(of(undefined));
        authenticateStub.mockReturnValue(throwError(() => error));
        loginService.logout(true);

        commonExpects();
        expect(alertServiceErrorStub).toHaveBeenCalledTimes(1);
    });

    function commonExpects() {
        expect(removeAuthTokenFromCachesStub).toHaveBeenCalledTimes(1);
        expect(removeAuthTokenFromCachesStub).toHaveBeenCalledWith();
        expect(authenticateStub).toHaveBeenCalledTimes(1);
        expect(authenticateStub).toHaveBeenCalledWith(undefined);
        expect(notificationServiceCleanUpStub).toHaveBeenCalledTimes(1);
        expect(notificationServiceCleanUpStub).toHaveBeenCalledWith();
        expect(alertServiceClearStub).toHaveBeenCalledTimes(1);
        expect(alertServiceClearStub).toHaveBeenCalledWith();
        expect(navigateByUrlStub).toHaveBeenCalledTimes(1);
        expect(navigateByUrlStub).toHaveBeenCalledWith('/');
    }
});
