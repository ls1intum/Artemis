import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HomeComponent } from './home.component';
import { AccountService } from 'app/core/auth/account.service';
import { LoginService } from 'app/core/login/login.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
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
import { of } from 'rxjs';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, SetupPasskeyModalComponent } from 'app/core/course/overview/setup-passkey-modal/setup-passkey-modal.component';
import { User } from 'app/core/user/user.model';
import { LocalStorageService } from 'app/shared/service/local-storage.service';

describe('HomeComponent', () => {
    setupTestBed({ zoneless: true });

    let component: HomeComponent;
    let fixture: ComponentFixture<HomeComponent>;
    let accountService: AccountService;
    let loginService: LoginService;
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
            imports: [MockRouterLinkDirective, SetupPasskeyModalComponent],
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
        }).compileComponents();

        localStorageService = TestBed.inject(LocalStorageService);
        localStorageService.clear();
        fixture = TestBed.createComponent(HomeComponent);
        component = fixture.componentInstance;
        accountService = TestBed.inject(AccountService);
        loginService = TestBed.inject(LoginService);
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

    describe('openSetupPasskeyModal', () => {
        it('should not open the modal if passkey feature is disabled', () => {
            component.isPasskeyEnabled = false;
            accountService.userIdentity.set({ askToSetupPasskey: true } as User);

            component.openSetupPasskeyModal();

            expect(component.showPasskeyModal()).toBe(false);
        });

        it('should not open the modal if the user has already registered a passkey', () => {
            component.isPasskeyEnabled = true;
            accountService.userIdentity.set({ askToSetupPasskey: false } as User);

            component.openSetupPasskeyModal();

            expect(component.showPasskeyModal()).toBe(false);
        });

        it('should open the modal if the passkey feature is enabled, the user is authenticated, and no passkey is registered', () => {
            component.isPasskeyEnabled = true;

            accountService.userIdentity.set({ askToSetupPasskey: true } as User);

            component.openSetupPasskeyModal();

            expect(component.showPasskeyModal()).toBe(true);
        });

        it('should return early if the user disabled the reminder for the current timeframe', () => {
            component.isPasskeyEnabled = true;
            const futureDate = new Date();
            futureDate.setDate(futureDate.getDate() + 1);
            localStorageService.store(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, futureDate);

            accountService.userIdentity.set({ askToSetupPasskey: true } as User);

            component.openSetupPasskeyModal();

            expect(component.showPasskeyModal()).toBe(false);
        });

        it('should not return early if the reminder date is in the past', () => {
            component.isPasskeyEnabled = true;
            const dateInPast = new Date();
            dateInPast.setDate(dateInPast.getDate() - 10);
            localStorageService.store(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, dateInPast);

            accountService.userIdentity.set({ askToSetupPasskey: true } as User);

            component.openSetupPasskeyModal();

            expect(component.showPasskeyModal()).toBe(true);
        });
    });
});
