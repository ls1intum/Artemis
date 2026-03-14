import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HomeComponent } from './home.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AccountService } from 'app/core/auth/account.service';
import { LoginService } from 'app/core/login/login.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
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
import { EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, SetupPasskeyModalComponent } from 'app/core/course/overview/setup-passkey-modal/setup-passkey-modal.component';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { User } from 'app/core/user/user.model';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { Saml2LoginComponent } from './saml2-login/saml2-login.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { RouterLink } from '@angular/router';
import { PasskeyAbortError } from 'app/core/user/settings/passkey-settings/entities/errors/passkey-abort.error';

describe('HomeComponent', () => {
    let component: HomeComponent;
    let fixture: ComponentFixture<HomeComponent>;
    let accountService: AccountService;
    let modalService: NgbModal;
    let loginService: LoginService;
    let webauthnService: WebauthnService;
    let localStorageService: LocalStorageService;

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
                { provide: NgbModal, useClass: MockNgbModalService },
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

        localStorageService = TestBed.inject(LocalStorageService);
        localStorageService.clear();
        fixture = TestBed.createComponent(HomeComponent);
        component = fixture.componentInstance;
        accountService = TestBed.inject(AccountService);
        modalService = TestBed.inject(NgbModal);
        loginService = TestBed.inject(LoginService);
        webauthnService = TestBed.inject(WebauthnService);
        fixture.detectChanges();
    });

    it('should create the component', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize with profile info and prefilled username', () => {
        expect(component.username).toBe('prefilledUsername');
        expect(component.isPasskeyEnabled).toBeFalse();
    });

    it('should validate form correctly', () => {
        component.username = 'testUser';
        component.password = 'password123';
        component.checkFormValidity();
        expect(component.isFormValid).toBeTrue();

        component.password = '';
        component.checkFormValidity();
        expect(component.isFormValid).toBeFalse();
    });

    it('should handle successful login', fakeAsync(() => {
        const loginSpy = jest.spyOn(loginService, 'login').mockResolvedValue();
        const handleLoginSuccessSpy = jest.spyOn(component as any, 'handleLoginSuccess');

        component.username = 'testUser';
        component.password = 'password123';
        component.rememberMe = true;

        component.login();
        tick();

        expect(component.isSubmittingLogin).toBeFalse();
        expect(loginSpy).toHaveBeenCalledWith({
            username: 'testUser',
            password: 'password123',
            rememberMe: true,
        });
        expect(handleLoginSuccessSpy).toHaveBeenCalled();
        expect(component.authenticationError).toBeFalse();
    }));

    it('should handle failed login', fakeAsync(() => {
        jest.spyOn(loginService, 'login').mockRejectedValue(new Error('Login failed'));

        component.username = 'testUser';
        component.password = 'wrongPassword';

        component.login();
        tick();

        expect(component.isSubmittingLogin).toBeFalse();
        expect(component.authenticationError).toBeTrue();
    }));

    it('should set and reset isSubmittingLogin flag', fakeAsync(() => {
        const loginSpy = jest.spyOn(loginService, 'login').mockResolvedValue();

        component.username = 'testUser';
        component.password = 'password123';

        component.login();
        expect(component.isSubmittingLogin).toBeTrue();

        tick();

        expect(component.isSubmittingLogin).toBeFalse();
        expect(loginSpy).toHaveBeenCalled();
    }));

    describe('openSetupPasskeyModal', () => {
        it('should not open the modal if passkey feature is disabled', () => {
            component.isPasskeyEnabled = false;
            const openModalSpy = jest.spyOn(modalService, 'open');
            accountService.userIdentity.set({ askToSetupPasskey: true } as User);

            component.openSetupPasskeyModal();

            expect(openModalSpy).not.toHaveBeenCalled();
        });

        it('should not open the modal if the user has already registered a passkey', () => {
            component.isPasskeyEnabled = true;
            const openModalSpy = jest.spyOn(modalService, 'open');
            accountService.userIdentity.set({ askToSetupPasskey: false } as User);

            component.openSetupPasskeyModal();

            expect(openModalSpy).not.toHaveBeenCalled();
        });

        it('should open the modal if the passkey feature is enabled, the user is authenticated, and no passkey is registered', () => {
            component.isPasskeyEnabled = true;
            const openModalSpy = jest.spyOn(modalService, 'open');

            accountService.userIdentity.set({ askToSetupPasskey: true } as User);

            component.openSetupPasskeyModal();

            expect(openModalSpy).toHaveBeenCalledWith(SetupPasskeyModalComponent, { size: 'lg', backdrop: 'static' });
        });

        it('should return early if the user disabled the reminder for the current timeframe', () => {
            component.isPasskeyEnabled = true;
            const futureDate = new Date();
            futureDate.setDate(futureDate.getDate() + 1);
            localStorageService.store(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, futureDate);

            accountService.userIdentity.set({ askToSetupPasskey: true } as User);
            const openModalSpy = jest.spyOn(modalService, 'open');

            component.openSetupPasskeyModal();

            expect(openModalSpy).not.toHaveBeenCalled();
        });

        it('should not return early if the reminder date is in the past', () => {
            component.isPasskeyEnabled = true;
            const dateInPast = new Date();
            dateInPast.setDate(dateInPast.getDate() - 10);
            localStorageService.store(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, dateInPast);

            accountService.userIdentity.set({ askToSetupPasskey: true } as User);
            const openModalSpy = jest.spyOn(modalService, 'open');

            component.openSetupPasskeyModal();

            expect(openModalSpy).toHaveBeenCalled();
        });
    });

    describe('prefillPasskeysIfPossible', () => {
        it('should call makePasskeyAutocompleteAvailable if passkey is enabled and conditional mediation is available', async () => {
            component.isPasskeyEnabled = true;
            const makePasskeyAutocompleteSpy = jest.spyOn(component, 'makePasskeyAutocompleteAvailable').mockResolvedValue(undefined);
            (window as any).PublicKeyCredential = {
                isConditionalMediationAvailable: jest.fn().mockResolvedValue(true),
            };

            await component.prefillPasskeysIfPossible();

            expect(window.PublicKeyCredential!.isConditionalMediationAvailable).toHaveBeenCalledOnce();
            expect(makePasskeyAutocompleteSpy).toHaveBeenCalledOnce();
        });

        it('should not call makePasskeyAutocompleteAvailable if passkey is disabled', async () => {
            component.isPasskeyEnabled = false;
            const makePasskeyAutocompleteSpy = jest.spyOn(component, 'makePasskeyAutocompleteAvailable');

            await component.prefillPasskeysIfPossible();

            expect(makePasskeyAutocompleteSpy).not.toHaveBeenCalled();
        });

        it('should not call makePasskeyAutocompleteAvailable if conditional mediation is unavailable', async () => {
            component.isPasskeyEnabled = true;
            const makePasskeyAutocompleteSpy = jest.spyOn(component, 'makePasskeyAutocompleteAvailable');
            (window as any).PublicKeyCredential = {
                isConditionalMediationAvailable: jest.fn().mockResolvedValue(false),
            };

            await component.prefillPasskeysIfPossible();

            expect(window.PublicKeyCredential!.isConditionalMediationAvailable).toHaveBeenCalledOnce();
            expect(makePasskeyAutocompleteSpy).not.toHaveBeenCalled();
        });

        it('should not throw if PublicKeyCredential is undefined', async () => {
            component.isPasskeyEnabled = true;
            (window as any).PublicKeyCredential = undefined;

            await expect(component.prefillPasskeysIfPossible()).resolves.not.toThrow();
        });
    });

    describe('makePasskeyAutocompleteAvailable', () => {
        it('should handle successful conditional mediation login', async () => {
            jest.spyOn(webauthnService, 'loginWithPasskey').mockResolvedValue(undefined);
            const handleLoginSuccessSpy = jest.spyOn(component as any, 'handleLoginSuccess');

            await component.makePasskeyAutocompleteAvailable();

            expect(webauthnService.loginWithPasskey).toHaveBeenCalledWith(true);
            expect(handleLoginSuccessSpy).toHaveBeenCalled();
        });

        it('should silently handle PasskeyAbortError', async () => {
            jest.spyOn(console, 'warn').mockImplementation(() => {});
            jest.spyOn(webauthnService, 'loginWithPasskey').mockRejectedValue(new PasskeyAbortError());
            const handleLoginSuccessSpy = jest.spyOn(component as any, 'handleLoginSuccess');

            await component.makePasskeyAutocompleteAvailable();

            expect(handleLoginSuccessSpy).not.toHaveBeenCalled();
        });

        it('should silently handle DOMException AbortError', async () => {
            jest.spyOn(console, 'warn').mockImplementation(() => {});
            jest.spyOn(webauthnService, 'loginWithPasskey').mockRejectedValue(new DOMException('Aborted', 'AbortError'));
            const handleLoginSuccessSpy = jest.spyOn(component as any, 'handleLoginSuccess');

            await component.makePasskeyAutocompleteAvailable();

            expect(handleLoginSuccessSpy).not.toHaveBeenCalled();
        });

        it('should retry once on NotAllowedError (user cancelled)', async () => {
            jest.spyOn(console, 'warn').mockImplementation(() => {});
            const notAllowedError = new DOMException('User cancelled', 'NotAllowedError');
            const loginSpy = jest.spyOn(webauthnService, 'loginWithPasskey').mockRejectedValue(notAllowedError);

            await component.makePasskeyAutocompleteAvailable();

            // First call + one retry = 2 calls
            expect(loginSpy).toHaveBeenCalledTimes(2);
            expect(loginSpy).toHaveBeenCalledWith(true);
        });
    });

    describe('ngOnDestroy', () => {
        it('should abort pending credential request on destroy', () => {
            const abortSpy = jest.spyOn(webauthnService, 'abortPendingCredentialRequest');

            component.ngOnDestroy();

            expect(abortSpy).toHaveBeenCalledOnce();
        });
    });
});
