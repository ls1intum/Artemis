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

    let authenticateStub: jest.SpyInstance;
    let authServerProviderStub: jest.SpyInstance;
    let alertServiceClearStub: jest.SpyInstance;
    let notificationServiceCleanUpStub: jest.SpyInstance;
    let navigateByUrlStub: jest.SpyInstance;

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

                authenticateStub = jest.spyOn(accountService, 'authenticate');
                authServerProviderStub = jest.spyOn(authServerProvider, 'logout');
                alertServiceClearStub = jest.spyOn(alertService, 'closeAll');
                notificationServiceCleanUpStub = jest.spyOn(notificationService, 'cleanUp');
                navigateByUrlStub = jest.spyOn(router, 'navigateByUrl');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should properly log out when every action is successful', () => {
        navigateByUrlStub.mockReturnValue(Promise.resolve(true));
        loginService.logout(true);

        commonExpects();
        expect(authServerProviderStub).toHaveBeenCalledOnce();
    });

    it('should logout properly and not call server logout on forceful logout', () => {
        navigateByUrlStub.mockReturnValue(Promise.resolve(true));
        loginService.logout(false);

        commonExpects();
        expect(authServerProviderStub).not.toHaveBeenCalled();
    });

    function commonExpects() {
        expect(authenticateStub).toHaveBeenCalledOnce();
        expect(authenticateStub).toHaveBeenCalledWith(undefined);
        expect(notificationServiceCleanUpStub).toHaveBeenCalledOnce();
        expect(notificationServiceCleanUpStub).toHaveBeenCalledWith();
        expect(alertServiceClearStub).toHaveBeenCalledOnce();
        expect(alertServiceClearStub).toHaveBeenCalledWith();
        expect(navigateByUrlStub).toHaveBeenCalledOnce();
        expect(navigateByUrlStub).toHaveBeenCalledWith('/');
    }
});
