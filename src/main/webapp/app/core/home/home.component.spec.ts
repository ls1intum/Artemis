import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
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
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { Subject, of } from 'rxjs';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Saml2LoginComponent } from './saml2-login/saml2-login.component';
import { RouterLink } from '@angular/router';
import { TumUiButtonComponent, TumUiCheckboxComponent, TumUiInputDirective, TumUiMessageComponent } from '@tumaet/ui-angular';

describe('HomeComponent', () => {
    let component: HomeComponent;
    let fixture: ComponentFixture<HomeComponent>;
    let loginService: LoginService;
    let webauthnService: WebauthnService;
    let httpMock: HttpTestingController;
    let translateService: TranslateService;

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
                remove: {
                    imports: [Saml2LoginComponent, RouterLink, TumUiButtonComponent, TumUiInputDirective, TumUiCheckboxComponent, TumUiMessageComponent],
                },
                add: {
                    imports: [
                        MockComponent(Saml2LoginComponent),
                        MockRouterLinkDirective,
                        MockComponent(TumUiButtonComponent),
                        MockDirective(TumUiInputDirective),
                        MockComponent(TumUiCheckboxComponent),
                        MockComponent(TumUiMessageComponent),
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(HomeComponent);
        component = fixture.componentInstance;
        loginService = TestBed.inject(LoginService);
        webauthnService = TestBed.inject(WebauthnService);
        httpMock = TestBed.inject(HttpTestingController);
        translateService = TestBed.inject(TranslateService);

        // Replace the mock property with a real Subject before detectChanges triggers the subscription
        (translateService as any).onLangChange = new Subject<any>();

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
        expect(component.isPasskeyEnabled()).toBe(false);
    });

    it('should validate identifier length and pattern correctly', () => {
        component.username = 'validUser';
        component.checkIdentifierValidity();
        expect(component.isIdentifierValid()).toBe(true);

        component.username = 'abc';
        component.checkIdentifierValidity();
        expect(component.isIdentifierValid()).toBe(false);
    });

    it('should reset stage state when goBack is triggered', () => {
        component.currentStage.set(2);
        component.password = 'somePassword';

        component.goBack();

        expect(component.currentStage()).toBe(1);
        expect(component.password).toBe('');
    });

    describe('onContinue', () => {
        it('should advance to Stage 2 and resolve PASSWORD method with explicit IdP name', () => {
            component.username = 'testUser';
            vi.spyOn(component, 'isIdentifierValid').mockReturnValue(true);

            component.onContinue();
            expect(component.isCheckingIdentifier()).toBe(true);

            const req = httpMock.expectOne('api/core/public/login-options?usernameOrEmail=testUser');
            expect(req.request.method).toBe('GET');
            req.flush({ loginMethod: 'PASSWORD', idpName: 'Custom Identity Provider' });

            expect(component.isCheckingIdentifier()).toBe(false);
            expect(component.loginMethod()).toBe('PASSWORD');
            expect(component.externalIdpName()).toBe('Custom Identity Provider');
            expect(component.currentStage()).toBe(2);
            expect(component.authenticationError()).toBe(false);
        });

        it('should use dynamic fallback translation when the server returns an empty or null idpName', () => {
            vi.spyOn(translateService, 'instant').mockReturnValue('University Credentials');
            component.username = 'oidcUser';
            vi.spyOn(component, 'isIdentifierValid').mockReturnValue(true);

            component.onContinue();

            const req = httpMock.expectOne('api/core/public/login-options?usernameOrEmail=oidcUser');
            req.flush({ loginMethod: 'OIDC', idpName: null });

            expect(component.loginMethod()).toBe('OIDC');
            expect(translateService.instant).toHaveBeenCalledWith('home.login.form.universityCredentials');
            expect(component.externalIdpName()).toBe('University Credentials');
            expect(component['isUsingFallbackIdpName']).toBe(true);
        });

        it('should accurately process SAML2 login option criteria from server configuration', () => {
            component.username = 'samlUser';
            vi.spyOn(component, 'isIdentifierValid').mockReturnValue(true);

            component.onContinue();

            const req = httpMock.expectOne('api/core/public/login-options?usernameOrEmail=samlUser');
            req.flush({ loginMethod: 'SAML2', idpName: 'SAML Provider' });

            expect(component.loginMethod()).toBe('SAML2');
            expect(component.currentStage()).toBe(2);
        });

        it('should default loginMethod to PASSWORD if server returns an unexpected method option', () => {
            component.username = 'invalidMethodUser';
            vi.spyOn(component, 'isIdentifierValid').mockReturnValue(true);

            component.onContinue();

            const req = httpMock.expectOne('api/core/public/login-options?usernameOrEmail=invalidMethodUser');
            req.flush({ loginMethod: 'UNKNOWN_JUNK_METHOD_STRING', idpName: 'Fallback Provider' });

            expect(component.loginMethod()).toBe('PASSWORD');
            expect(component.currentStage()).toBe(2);
        });

        it('should default smoothly to PASSWORD layout options upon generic HTTP failure status', () => {
            component.username = 'networkErrorUser';
            vi.spyOn(component, 'isIdentifierValid').mockReturnValue(true);

            component.onContinue();

            const req = httpMock.expectOne('api/core/public/login-options?usernameOrEmail=networkErrorUser');
            req.error(new ProgressEvent('Network error'));

            expect(component.isCheckingIdentifier()).toBe(false);
            expect(component.loginMethod()).toBe('PASSWORD');
            expect(component.externalIdpName()).toBeNull();
            expect(component.currentStage()).toBe(2);
        });
    });

    describe('Language Translation Synchronization', () => {
        it('should re-translate university credentials dynamically upon language change event if active', () => {
            vi.spyOn(translateService, 'instant').mockImplementation((key) => {
                if (key === 'home.login.form.universityCredentials') return 'Universitäre Zugangsdaten';
                return '';
            });
            component['isUsingFallbackIdpName'] = true;

            (translateService.onLangChange as Subject<any>).next({ lang: 'de' });

            expect(translateService.instant).toHaveBeenCalledWith('home.login.form.universityCredentials');
            expect(component.externalIdpName()).toBe('Universitäre Zugangsdaten');
        });
    });

    describe('Traditional Password Login', () => {
        it('should handle successful login', async () => {
            const loginSpy = vi.spyOn(loginService, 'login').mockResolvedValue(undefined);
            const handleLoginSuccessSpy = vi.spyOn(component as any, 'handleLoginSuccess').mockImplementation(() => {});

            component.username = 'testUser';
            component.password = 'password123';
            component.rememberMe = true;

            await component.login();
            await fixture.whenStable();

            expect(component.isSubmittingLogin()).toBe(false);
            expect(loginSpy).toHaveBeenCalledWith({
                username: 'testUser',
                password: 'password123',
                rememberMe: true,
            });
            expect(handleLoginSuccessSpy).toHaveBeenCalled();
            expect(component.authenticationError()).toBe(false);
        });

        it('should handle failed login', async () => {
            vi.spyOn(loginService, 'login').mockRejectedValue(new Error('Login failed'));

            component.username = 'testUser';
            component.password = 'wrongPassword';

            await component.login();
            await fixture.whenStable();

            expect(component.isSubmittingLogin()).toBe(false);
            expect(component.authenticationError()).toBe(true);
        });

        it('should set and reset isSubmittingLogin flag', async () => {
            const loginSpy = vi.spyOn(loginService, 'login').mockResolvedValue(undefined);
            vi.spyOn(component as any, 'handleLoginSuccess').mockImplementation(() => {});

            component.username = 'testUser';
            component.password = 'password123';

            const loginPromise = component.login();
            expect(component.isSubmittingLogin()).toBe(true);

            await loginPromise;
            await fixture.whenStable();
            expect(component.isSubmittingLogin()).toBe(false);
            expect(loginSpy).toHaveBeenCalled();
        });
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

    describe('loginWithOidc', () => {
        it('should handle initiation of OIDC login with rememberMe true without triggering handleLoginSuccess', async () => {
            const loginOidcSpy = vi.spyOn(loginService, 'loginOIDC').mockResolvedValue(undefined);
            const handleLoginSuccessSpy = vi.spyOn(component as any, 'handleLoginSuccess').mockImplementation(() => {});

            component.rememberMe = true;

            component.loginWithOidc();
            await fixture.whenStable();

            expect(loginOidcSpy).toHaveBeenCalledWith(true);
            // OIDC redirect happens external, so handleLoginSuccess is NOT called immediately
            expect(handleLoginSuccessSpy).not.toHaveBeenCalled();
            expect(component.authenticationError()).toBe(false);
        });

        it('should handle initiation of OIDC login with rememberMe false', async () => {
            const loginOidcSpy = vi.spyOn(loginService, 'loginOIDC').mockResolvedValue(undefined);
            const handleLoginSuccessSpy = vi.spyOn(component as any, 'handleLoginSuccess').mockImplementation(() => {});

            component.rememberMe = false;

            component.loginWithOidc();
            await fixture.whenStable();

            expect(loginOidcSpy).toHaveBeenCalledWith(false);
            expect(handleLoginSuccessSpy).not.toHaveBeenCalled();
            expect(component.authenticationError()).toBe(false);
        });

        it('should handle failed OIDC login', async () => {
            vi.spyOn(loginService, 'loginOIDC').mockRejectedValue(new Error('OIDC failed'));

            component.loginWithOidc();
            await fixture.whenStable();

            expect(component.authenticationError()).toBe(true);
            expect(component.isSubmittingLogin()).toBe(false);
        });

        it('should execute template branch for loading icon', async () => {
            // Enable isOidcEnabled flag
            component.currentStage.set(2);
            component.loginMethod.set('OIDC');

            // Mock loginOIDC
            let resolveLogin: () => void = () => {};
            const pendingPromise = new Promise<void>((resolve) => {
                resolveLogin = resolve;
            });
            vi.spyOn(loginService, 'loginOIDC').mockReturnValue(pendingPromise);
            vi.spyOn(component as any, 'handleLoginSuccess').mockImplementation(() => {});

            component.loginWithOidc();
            fixture.detectChanges();

            // Verify that login button is rendered
            expect(component.isSubmittingLogin()).toBe(true);
            const button = fixture.nativeElement.querySelector('#oidc-login-button');
            expect(button).toBeTruthy();

            // Clean
            resolveLogin();
            fixture.detectChanges();
        });
    });

    describe('bfcache pageshow navigation', () => {
        it('should reset loading state when page is restored from browser cache', () => {
            component.isSubmittingLogin.set(true);
            component.isCheckingIdentifier.set(true);

            // Dispatch persisted pageshow event simulating Back button navigation
            const event = new PageTransitionEvent('pageshow', { persisted: true });
            window.dispatchEvent(event);

            expect(component.isSubmittingLogin()).toBe(false);
            expect(component.isCheckingIdentifier()).toBe(false);
        });
    });

    describe('prefillPasskeysIfPossible', () => {
        it('should call startConditionalMediation if passkey is enabled and conditional mediation is available', async () => {
            component.isPasskeyEnabled.set(true);
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
            component.isPasskeyEnabled.set(false);
            const startSpy = vi.spyOn(webauthnService, 'startConditionalMediation');

            await component.prefillPasskeysIfPossible();

            expect(startSpy).not.toHaveBeenCalled();
        });

        it('should not call startConditionalMediation if conditional mediation is unavailable', async () => {
            component.isPasskeyEnabled.set(true);
            const startSpy = vi.spyOn(webauthnService, 'startConditionalMediation');
            (window as any).PublicKeyCredential = {
                isConditionalMediationAvailable: vi.fn().mockResolvedValue(false),
            };

            await component.prefillPasskeysIfPossible();

            expect(window.PublicKeyCredential!.isConditionalMediationAvailable).toHaveBeenCalledOnce();
            expect(startSpy).not.toHaveBeenCalled();
        });

        it('should not throw if PublicKeyCredential is undefined', async () => {
            component.isPasskeyEnabled.set(true);
            (window as any).PublicKeyCredential = undefined;

            await expect(component.prefillPasskeysIfPossible()).resolves.not.toThrow();
        });
    });

    it('should set authenticationError and display alert if loginError=deactivated query param is present', () => {
        const route = TestBed.inject(ActivatedRoute);
        // Imitate there is "loginError=deactivated" attribute
        route.queryParams = of({ loginError: 'deactivated' });

        const alertService = TestBed.inject(AlertService) as any;
        const alertSpy = vi.spyOn(alertService, 'error');

        const customFixture = TestBed.createComponent(HomeComponent);
        const customComponent = customFixture.componentInstance;
        customFixture.detectChanges();

        // Verify red banner is shown
        expect(customComponent.authenticationError()).toBe(true);
        // Verify the correct error message is shown
        expect(alertSpy).toHaveBeenCalledWith('home.errors.loginDeactivated');

        customFixture.destroy();
    });

    describe('ngOnDestroy', () => {
        it('should stop conditional mediation on destroy', () => {
            const stopSpy = vi.spyOn(webauthnService, 'stopConditionalMediation');

            component.ngOnDestroy();

            expect(stopSpy).toHaveBeenCalledOnce();
        });
    });
});
