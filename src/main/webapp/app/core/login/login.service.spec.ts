import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockAuthServerProviderService } from 'test/helpers/mocks/service/mock-auth-server-provider.service';
import { AccountService } from 'app/core/auth/account.service';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { AuthServerProvider, Credentials } from 'app/core/auth/auth-jwt.service';
import { LoginService } from 'app/core/login/login.service';
import { TestBed } from '@angular/core/testing';
import { AlertService } from 'app/shared/service/alert.service';
import { Router } from '@angular/router';
import { MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';

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

    describe('test login', () => {
        it('should login successfully with valid credentials', async () => {
            const credentials = new Credentials('testuser', 'password', true);
            const loginSpy = vi.spyOn(authServerProvider, 'login').mockReturnValue(of({}));
            const identitySpy = vi.spyOn(accountService, 'identity').mockResolvedValue({ id: 1 } as any);

            await loginService.login(credentials);

            expect(loginSpy).toHaveBeenCalledWith(credentials);
            expect(identitySpy).toHaveBeenCalledWith(true);
        });

        it('should reject and logout on login error', async () => {
            const credentials = new Credentials('testuser', 'wrongpassword', false);
            const loginError = new Error('Invalid credentials');
            vi.spyOn(authServerProvider, 'login').mockReturnValue(throwError(() => loginError));
            navigateByUrlStub.mockReturnValue(Promise.resolve(true));

            await expect(loginService.login(credentials)).rejects.toThrow('Invalid credentials');

            // Verify logout was called (forceful = false)
            expect(authenticateStub).toHaveBeenCalledWith(undefined);
        });
    });

    describe('test loginSAML2', () => {
        it('should login via SAML2 successfully with rememberMe true', async () => {
            const loginSpy = vi.spyOn(authServerProvider, 'loginSAML2').mockReturnValue(of({}));
            const identitySpy = vi.spyOn(accountService, 'identity').mockResolvedValue({ id: 1 } as any);

            await loginService.loginSAML2(true);

            expect(loginSpy).toHaveBeenCalledWith(true);
            expect(identitySpy).toHaveBeenCalledWith(true);
        });

        it('should login via SAML2 successfully with rememberMe false', async () => {
            const loginSpy = vi.spyOn(authServerProvider, 'loginSAML2').mockReturnValue(of({}));
            const identitySpy = vi.spyOn(accountService, 'identity').mockResolvedValue({ id: 1 } as any);

            await loginService.loginSAML2(false);

            expect(loginSpy).toHaveBeenCalledWith(false);
            expect(identitySpy).toHaveBeenCalledWith(true);
        });

        it('should reject and logout on SAML2 login error', async () => {
            const loginError = new Error('SAML2 authentication failed');
            vi.spyOn(authServerProvider, 'loginSAML2').mockReturnValue(throwError(() => loginError));
            navigateByUrlStub.mockReturnValue(Promise.resolve(true));

            await expect(loginService.loginSAML2(true)).rejects.toThrow('SAML2 authentication failed');

            // Verify logout was called (forceful = false)
            expect(authenticateStub).toHaveBeenCalledWith(undefined);
        });
    });

    describe('test lastLogoutWasForceful', () => {
        it('should return true when logout was forceful', () => {
            navigateByUrlStub.mockReturnValue(Promise.resolve(true));
            loginService.logout(false); // wasInitiatedByUser = false means forceful logout

            expect(loginService.lastLogoutWasForceful()).toBe(true);
        });

        it('should return false when logout was initiated by user', () => {
            navigateByUrlStub.mockReturnValue(Promise.resolve(true));
            loginService.logout(true); // wasInitiatedByUser = true means voluntary logout

            expect(loginService.lastLogoutWasForceful()).toBe(false);
        });

        it('should return false initially', () => {
            expect(loginService.lastLogoutWasForceful()).toBe(false);
        });
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
