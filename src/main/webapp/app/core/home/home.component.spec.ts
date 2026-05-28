import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HomeComponent } from './home.component';
import { AccountService } from 'app/core/auth/account.service';
import { LoginService } from 'app/core/login/login.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { WebauthnService } from 'app/account/user/settings/passkey-settings/webauthn.service';
import { WebauthnApiService } from 'app/account/user/settings/passkey-settings/webauthn-api.service';
import { MockComponent, MockProvider } from 'ng-mocks';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { of } from 'rxjs';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Saml2LoginComponent } from './saml2-login/saml2-login.component';
import { ButtonComponent } from 'app/ui/components/buttons/button/button.component';
import { RouterLink } from '@angular/router';

describe('HomeComponent', () => {
    setupTestBed({ zoneless: true });

    let component: HomeComponent;
    let fixture: ComponentFixture<HomeComponent>;
    let loginService: LoginService;
    let webauthnService: WebauthnService;

    let router: MockRouter;

    const route = {
        data: of({}),
        children: [],
        queryParams: of({}),
    } as any as ActivatedRoute;

    beforeEach(async () => {
        router = new MockRouter();
        router.setUrl('');

        await TestBed.configureTestingModule({
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useValue: router },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(LoginService),
                MockProvider(EventManager),
                MockProvider(AlertService),
                MockProvider(WebauthnService),
                MockProvider(WebauthnApiService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(HomeComponent, {
                remove: { imports: [Saml2LoginComponent, ButtonComponent, RouterLink] },
                add: { imports: [MockComponent(Saml2LoginComponent), MockComponent(ButtonComponent), MockRouterLinkDirective] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(HomeComponent);
        component = fixture.componentInstance;
        loginService = TestBed.inject(LoginService);
        webauthnService = TestBed.inject(WebauthnService);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with profile info and prefilled username', () => {
        expect(component.username).toBe('prefilledUsername');
        expect(component.isPasskeyEnabled).toBe(false);
    });

    it('should validate form correctly', () => {
        component.username = 'testUser';
        component.password = 'password123';
        component.checkFormValidity();
        expect(component.isFormValid).toBe(true);

        component.password = '';
        component.checkFormValidity();
        expect(component.isFormValid).toBe(false);
    });

    it('should handle successful login', async () => {
        const loginSpy = vi.spyOn(loginService, 'login').mockResolvedValue(undefined);
        const handleLoginSuccessSpy = vi.spyOn(component as any, 'handleLoginSuccess').mockImplementation(() => {});

        component.username = 'testUser';
        component.password = 'password123';
        component.rememberMe = true;

        await component.login();
        await fixture.whenStable();

        expect(component.isSubmittingLogin).toBe(false);
        expect(loginSpy).toHaveBeenCalledWith({
            username: 'testUser',
            password: 'password123',
            rememberMe: true,
        });
        expect(handleLoginSuccessSpy).toHaveBeenCalled();
        expect(component.authenticationError).toBe(false);
    });

    it('should handle failed login', async () => {
        vi.spyOn(loginService, 'login').mockRejectedValue(new Error('Login failed'));

        component.username = 'testUser';
        component.password = 'wrongPassword';

        await component.login();
        await fixture.whenStable();

        expect(component.isSubmittingLogin).toBe(false);
        expect(component.authenticationError).toBe(true);
    });

    it('should set and reset isSubmittingLogin flag', async () => {
        const loginSpy = vi.spyOn(loginService, 'login').mockResolvedValue(undefined);
        vi.spyOn(component as any, 'handleLoginSuccess').mockImplementation(() => {});

        component.username = 'testUser';
        component.password = 'password123';

        const loginPromise = component.login();
        expect(component.isSubmittingLogin).toBe(true);

        await loginPromise;
        await fixture.whenStable();
        expect(component.isSubmittingLogin).toBe(false);
        expect(loginSpy).toHaveBeenCalled();
    });

    describe('loginWithPasskey', () => {
        it('should handle login success', async () => {
            const loginWithPasskeySpy = vi.spyOn(webauthnService, 'loginWithPasskey').mockResolvedValue(undefined);
            const handleLoginSuccessSpy = vi.spyOn(component as any, 'handleLoginSuccess').mockImplementation(() => {});

            await component.loginWithPasskey();

            expect(loginWithPasskeySpy).toHaveBeenCalledOnce();
            expect(handleLoginSuccessSpy).toHaveBeenCalledOnce();
        });

        it('should restart passkey autofill after user aborts passkey login', async () => {
            const cancellationError = new DOMException('User cancelled', 'NotAllowedError');
            vi.spyOn(webauthnService, 'loginWithPasskey').mockRejectedValue(cancellationError);
            const prefillPasskeysSpy = vi.spyOn(component, 'prefillPasskeysIfPossible').mockResolvedValue(undefined);
            const handleLoginSuccessSpy = vi.spyOn(component as any, 'handleLoginSuccess').mockImplementation(() => {});

            await expect(component.loginWithPasskey()).resolves.toBeUndefined();

            expect(prefillPasskeysSpy).toHaveBeenCalledOnce();
            expect(handleLoginSuccessSpy).not.toHaveBeenCalled();
        });

        it('should rethrow non-abort passkey login errors', async () => {
            const networkError = new Error('Network error');
            vi.spyOn(webauthnService, 'loginWithPasskey').mockRejectedValue(networkError);
            const prefillPasskeysSpy = vi.spyOn(component, 'prefillPasskeysIfPossible').mockResolvedValue(undefined);
            const handleLoginSuccessSpy = vi.spyOn(component as any, 'handleLoginSuccess').mockImplementation(() => {});

            await expect(component.loginWithPasskey()).rejects.toThrow(networkError);

            expect(prefillPasskeysSpy).not.toHaveBeenCalled();
            expect(handleLoginSuccessSpy).not.toHaveBeenCalled();
        });
    });

    describe('prefillPasskeysIfPossible', () => {
        it('should call startConditionalMediation if passkey is enabled and conditional mediation is available', async () => {
            component.isPasskeyEnabled = true;
            const startSpy = vi.spyOn(webauthnService, 'startConditionalMediation');
            (window as any).PublicKeyCredential = {
                isConditionalMediationAvailable: vi.fn().mockResolvedValue(true),
            };

            await component.prefillPasskeysIfPossible();

            expect(window.PublicKeyCredential!.isConditionalMediationAvailable).toHaveBeenCalledOnce();
            expect(startSpy).toHaveBeenCalledOnce();
            expect(startSpy).toHaveBeenCalledWith(expect.any(Function), expect.any(Function));
        });

        it('should not call startConditionalMediation if passkey is disabled', async () => {
            component.isPasskeyEnabled = false;
            const startSpy = vi.spyOn(webauthnService, 'startConditionalMediation');

            await component.prefillPasskeysIfPossible();

            expect(startSpy).not.toHaveBeenCalled();
        });

        it('should not call startConditionalMediation if conditional mediation is unavailable', async () => {
            component.isPasskeyEnabled = true;
            const startSpy = vi.spyOn(webauthnService, 'startConditionalMediation');
            (window as any).PublicKeyCredential = {
                isConditionalMediationAvailable: vi.fn().mockResolvedValue(false),
            };

            await component.prefillPasskeysIfPossible();

            expect(window.PublicKeyCredential!.isConditionalMediationAvailable).toHaveBeenCalledOnce();
            expect(startSpy).not.toHaveBeenCalled();
        });

        it('should not throw if PublicKeyCredential is undefined', async () => {
            component.isPasskeyEnabled = true;
            (window as any).PublicKeyCredential = undefined;

            await expect(component.prefillPasskeysIfPossible()).resolves.not.toThrow();
        });
    });

    describe('ngOnDestroy', () => {
        it('should stop conditional mediation on destroy', () => {
            const stopSpy = vi.spyOn(webauthnService, 'stopConditionalMediation');

            component.ngOnDestroy();

            expect(stopSpy).toHaveBeenCalledOnce();
        });
    });
});
