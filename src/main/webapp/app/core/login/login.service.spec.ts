import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
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
    setupTestBed({ zoneless: true });

    let accountService: AccountService;
    let authServerProvider: AuthServerProvider;
    let router: Router;
    let alertService: AlertService;
    let loginService: LoginService;

    let authenticateStub: ReturnType<typeof vi.spyOn>;
    let authServerProviderLogoutStub: ReturnType<typeof vi.spyOn>;
    let authServerProviderClearStub: ReturnType<typeof vi.spyOn>;
    let alertServiceClearStub: ReturnType<typeof vi.spyOn>;
    let navigateByUrlStub: ReturnType<typeof vi.spyOn>;

    beforeEach(async () => {
        TestBed.configureTestingModule({
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: AuthServerProvider, useClass: MockAuthServerProviderService },
                { provide: Router, useClass: MockRouter },
                MockProvider(AlertService),
            ],
        });
        await TestBed.compileComponents();

        accountService = TestBed.inject(AccountService);
        authServerProvider = TestBed.inject(AuthServerProvider);
        router = TestBed.inject(Router);
        alertService = TestBed.inject(AlertService);
        loginService = TestBed.inject(LoginService);

        authenticateStub = vi.spyOn(accountService, 'authenticate');
        authServerProviderLogoutStub = vi.spyOn(authServerProvider, 'logout');
        authServerProviderClearStub = vi.spyOn(authServerProvider, 'clearCaches');
        alertServiceClearStub = vi.spyOn(alertService, 'closeAll');
        navigateByUrlStub = vi.spyOn(router, 'navigateByUrl');
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should properly log out when every action is successful', () => {
        navigateByUrlStub.mockReturnValue(Promise.resolve(true));
        loginService.logout(true);

        commonExpects();
        expect(authServerProviderLogoutStub).toHaveBeenCalledOnce();
    });

    it('should logout properly and not call server logout on forceful logout', () => {
        navigateByUrlStub.mockReturnValue(Promise.resolve(true));
        loginService.logout(false);

        commonExpects();
        expect(authServerProviderLogoutStub).not.toHaveBeenCalled();
    });

    function commonExpects() {
        expect(authenticateStub).toHaveBeenCalledOnce();
        expect(authenticateStub).toHaveBeenCalledWith(undefined);
        expect(alertServiceClearStub).toHaveBeenCalledOnce();
        expect(alertServiceClearStub).toHaveBeenCalledWith();
        expect(navigateByUrlStub).toHaveBeenCalledOnce();
        expect(navigateByUrlStub).toHaveBeenCalledWith('/');
        expect(authServerProviderClearStub).toHaveBeenCalledOnce();
    }
});
