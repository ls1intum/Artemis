import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HomeComponent, USER_CANCELLED_LOGIN_WITH_PASSKEY_ERROR } from './home.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AccountService } from 'app/core/auth/account.service';
import { LoginService } from 'app/core/login/login.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { StateStorageService } from 'app/core/auth/state-storage.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
import { MockProvider } from 'ng-mocks';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ActivatedRoute, Router } from '@angular/router';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, SetupPasskeyModalComponent } from 'app/core/course/overview/setup-passkey-modal/setup-passkey-modal.component';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { User } from 'app/core/user/user.model';
import { InvalidCredentialError } from 'app/core/user/settings/passkey-settings/entities/invalid-credential-error';
import { PasskeyAbortError } from 'app/core/user/settings/passkey-settings/entities/passkey-abort-error';

describe('HomeComponent', () => {
    let component: HomeComponent;
    let fixture: ComponentFixture<HomeComponent>;
    let accountService: AccountService;
    let modalService: NgbModal;
    let loginService: LoginService;
    let webauthnService: WebauthnService;
    let webauthnApiService: WebauthnApiService;
    let alertService: AlertService;

    let router: MockRouter;

    const route = {
        data: of({}),
        children: [],
        queryParams: of({}),
    } as any as ActivatedRoute;

    beforeEach(async () => {
        router = new MockRouter();
        router.setUrl('');

        localStorage.clear();

        await TestBed.configureTestingModule({
            imports: [MockRouterLinkDirective, RouterTestingModule, SetupPasskeyModalComponent],
            providers: [
                { provide: AccountService, useClass: MockAccountService },
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useValue: router },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
                MockProvider(LoginService),
                MockProvider(StateStorageService),
                MockProvider(EventManager),
                MockProvider(AlertService),
                MockProvider(WebauthnService),
                MockProvider(WebauthnApiService),
                MockProvider(NgbModal),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(HomeComponent);
        component = fixture.componentInstance;
        accountService = TestBed.inject(AccountService);
        modalService = TestBed.inject(NgbModal);
        loginService = TestBed.inject(LoginService);
        webauthnService = TestBed.inject(WebauthnService);
        webauthnApiService = TestBed.inject(WebauthnApiService);
        alertService = TestBed.inject(AlertService);

        jest.spyOn(console, 'error').mockImplementation(() => {});
        jest.spyOn(console, 'warn').mockImplementation(() => {});

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

    it('should handle successful login', async () => {
        const loginSpy = jest.spyOn(loginService, 'login').mockResolvedValue();
        const handleLoginSuccessSpy = jest.spyOn(component as any, 'handleLoginSuccess');

        component.username = 'testUser';
        component.password = 'password123';
        component.rememberMe = true;

        await component.login();

        expect(component.isSubmittingLogin).toBeFalse();
        expect(loginSpy).toHaveBeenCalledWith({
            username: 'testUser',
            password: 'password123',
            rememberMe: true,
        });
        expect(handleLoginSuccessSpy).toHaveBeenCalled();
        expect(component.authenticationError).toBeFalse();
    });

    it('should handle failed login', async () => {
        jest.spyOn(loginService, 'login').mockRejectedValue(new Error('Login failed'));

        component.username = 'testUser';
        component.password = 'wrongPassword';

        await component.login();

        expect(component.isSubmittingLogin).toBeFalse();
        expect(component.authenticationError).toBeTrue();
    });

    it('should set and reset isSubmittingLogin flag', async () => {
        const loginSpy = jest.spyOn(loginService, 'login').mockResolvedValue();

        component.username = 'testUser';
        component.password = 'password123';

        const loginPromise = component.login();
        expect(component.isSubmittingLogin).toBeTrue();

        await loginPromise;
        expect(component.isSubmittingLogin).toBeFalse();
        expect(loginSpy).toHaveBeenCalled();
    });

    describe('openSetupPasskeyModal', () => {
        it('should not open the modal if passkey feature is disabled', () => {
            component.isPasskeyEnabled = false;
            const openModalSpy = jest.spyOn(modalService, 'open');
            accountService.userIdentity = { askToSetupPasskey: true } as User;

            component.openSetupPasskeyModal();

            expect(openModalSpy).not.toHaveBeenCalled();
        });

        it('should not open the modal if the user has already registered a passkey', () => {
            component.isPasskeyEnabled = true;
            const openModalSpy = jest.spyOn(modalService, 'open');
            accountService.userIdentity = { askToSetupPasskey: false } as User;

            component.openSetupPasskeyModal();

            expect(openModalSpy).not.toHaveBeenCalled();
        });

        it('should open the modal if the passkey feature is enabled, the user is authenticated, and no passkey is registered', () => {
            component.isPasskeyEnabled = true;
            const openModalSpy = jest.spyOn(modalService, 'open');

            accountService.userIdentity = { askToSetupPasskey: true } as User;

            component.openSetupPasskeyModal();

            expect(openModalSpy).toHaveBeenCalledWith(SetupPasskeyModalComponent, { size: 'lg', backdrop: 'static' });
        });

        it('should return early if the user disabled the reminder for the current timeframe', () => {
            component.isPasskeyEnabled = true;
            const futureDate = new Date();
            futureDate.setDate(futureDate.getDate() + 1);
            localStorage.setItem(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, futureDate.toISOString());

            accountService.userIdentity = { askToSetupPasskey: true } as User;
            const openModalSpy = jest.spyOn(modalService, 'open');

            component.openSetupPasskeyModal();

            expect(openModalSpy).not.toHaveBeenCalled();
        });

        it('should not return early if the reminder date is in the past', () => {
            component.isPasskeyEnabled = true;
            const dateInPast = new Date();
            dateInPast.setDate(dateInPast.getDate() - 10);
            localStorage.setItem(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, dateInPast.toISOString());

            accountService.userIdentity = { askToSetupPasskey: true } as User;
            const openModalSpy = jest.spyOn(modalService, 'open');

            component.openSetupPasskeyModal();

            expect(openModalSpy).toHaveBeenCalled();
        });
    });

    describe('loginWithPasskey', () => {
        it('should handle successful passkey login', async () => {
            const mockCredential = { type: 'public-key' } as PublicKeyCredential;
            jest.spyOn(webauthnService, 'getCredential').mockResolvedValue(mockCredential);
            jest.spyOn(webauthnApiService, 'loginWithPasskey').mockResolvedValue();
            const handleLoginSuccessSpy = jest.spyOn(component as any, 'handleLoginSuccess');

            await component.loginWithPasskey();

            expect(handleLoginSuccessSpy).toHaveBeenCalled();
        });

        it('should handle invalid credential error on passkey login', async () => {
            jest.spyOn(webauthnService, 'getCredential').mockRejectedValue(new InvalidCredentialError());
            const alertSpy = jest.spyOn(alertService, 'addErrorAlert');

            await expect(component.loginWithPasskey()).rejects.toThrow(Error);
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
        });

        it('should handle generic login error on passkey login', async () => {
            jest.spyOn(webauthnService, 'getCredential').mockRejectedValue(new Error('Login failed'));
            const alertSpy = jest.spyOn(alertService, 'addErrorAlert');

            await expect(component.loginWithPasskey()).rejects.toThrow('Login failed');
            expect(alertSpy).toHaveBeenCalledWith('artemisApp.userSettings.passkeySettingsPage.error.login');
        });

        it('should fail silently when user cancels passkey login', async () => {
            const makePasskeyAutocompleteAvailableSpy = jest.spyOn(component as any, 'makePasskeyAutocompleteAvailable');
            jest.spyOn(alertService, 'addErrorAlert').mockImplementation(() => {}); // Mock addErrorAlert
            jest.spyOn(webauthnService, 'getCredential').mockRejectedValue({ name: USER_CANCELLED_LOGIN_WITH_PASSKEY_ERROR });

            await component.loginWithPasskey();

            expect(makePasskeyAutocompleteAvailableSpy).toHaveBeenCalled();
            expect(alertService.addErrorAlert).not.toHaveBeenCalled();
        });

        it('should fail silently on PasskeyAbortError', async () => {
            const makePasskeyAutocompleteAvailableSpy = jest.spyOn(component as any, 'makePasskeyAutocompleteAvailable');
            jest.spyOn(alertService, 'addErrorAlert').mockImplementation(() => {}); // Mock addErrorAlert
            jest.spyOn(webauthnService, 'getCredential').mockRejectedValue(new PasskeyAbortError('Passkey process aborted'));

            await component.loginWithPasskey();

            expect(makePasskeyAutocompleteAvailableSpy).not.toHaveBeenCalled();
            expect(alertService.addErrorAlert).not.toHaveBeenCalled();
        });

        it('should fail silently on OperationError with pending request', async () => {
            jest.spyOn(alertService, 'addErrorAlert').mockImplementation(() => {}); // Mock addErrorAlert
            jest.spyOn(webauthnService, 'getCredential').mockRejectedValue({
                name: 'OperationError',
                message: 'A request is already pending.',
            });

            await component.loginWithPasskey();

            expect(alertService.addErrorAlert).not.toHaveBeenCalled();
        });
    });
});
