import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockAuthServerProviderService } from 'test/helpers/mocks/service/mock-auth-server-provider.service';
import { AccountService } from 'app/core/auth/account.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { LoginService } from 'app/core/login/login.service';
import { AuthServerProvider } from 'app/core/auth/auth-jwt.service';
import { TestBed } from '@angular/core/testing';
import { AlertService } from 'app/shared/service/alert.service';
import { Router } from '@angular/router';
import { MockProvider } from 'ng-mocks';

describe('LoginService', () => {
    let accountService: AccountService;
    let authServerProvider: AuthServerProvider;
    let router: Router;
    let alertService: AlertService;
    let loginService: LoginService;

    let authenticateStub: jest.SpyInstance;
    let authServerProviderStub: jest.SpyInstance;
    let alertServiceClearStub: jest.SpyInstance;
    let navigateByUrlStub: jest.SpyInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: AuthServerProvider, useClass: MockAuthServerProviderService },
                { provide: Router, useClass: MockRouter },
                MockProvider(AlertService),
            ],
        })
            .compileComponents()
            .then(() => {
                accountService = TestBed.inject(AccountService);
                authServerProvider = TestBed.inject(AuthServerProvider);
                router = TestBed.inject(Router);
                alertService = TestBed.inject(AlertService);
                loginService = TestBed.inject(LoginService);

                authenticateStub = jest.spyOn(accountService, 'authenticate');
                authServerProviderStub = jest.spyOn(authServerProvider, 'logout');
                alertServiceClearStub = jest.spyOn(alertService, 'closeAll');
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
        expect(alertServiceClearStub).toHaveBeenCalledOnce();
        expect(alertServiceClearStub).toHaveBeenCalledWith();
        expect(navigateByUrlStub).toHaveBeenCalledOnce();
        expect(navigateByUrlStub).toHaveBeenCalledWith('/');
    }
});
