import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Subject } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, SetupPasskeyModalComponent } from './setup-passkey-modal.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AlertService } from 'app/foundation/service/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/account/user/user.model';
import { MODULE_FEATURE_PASSKEY } from 'app/app.constants';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

describe('SetupPasskeyModalComponent', () => {
    let component: SetupPasskeyModalComponent;
    let fixture: ComponentFixture<SetupPasskeyModalComponent>;
    let localStorageService: LocalStorageService;

    beforeEach(async () => {
        const storageMap = new Map<string, string>();
        vi.stubGlobal('localStorage', {
            getItem: vi.fn((key: string) => storageMap.get(key) ?? null),
            setItem: vi.fn((key: string, value: string) => storageMap.set(key, value)),
            removeItem: vi.fn((key: string) => storageMap.delete(key)),
            clear: vi.fn(() => storageMap.clear()),
        });

        await TestBed.configureTestingModule({
            imports: [SetupPasskeyModalComponent, MockPipe(TranslatePipe), MockDirective(TranslateDirective)],
            providers: [
                MockProvider(AlertService),
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(SetupPasskeyModalComponent, {
                remove: { imports: [TranslatePipe] },
                add: { imports: [MockPipe(TranslatePipe)] },
            })
            .compileComponents();

        fixture = TestBed.createComponent(SetupPasskeyModalComponent);
        component = fixture.componentInstance;
        localStorageService = TestBed.inject(LocalStorageService);
        localStorageService.clear();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should close the modal by setting visible to false', () => {
        component.visible.set(true);

        component.closeModal();

        expect(component.visible()).toBe(false);
    });

    it('should set reminder date in localStorage and close the modal', () => {
        const localStorageServiceSpy = vi.spyOn(localStorageService, 'store');
        component.visible.set(true);

        const expectedDateOnlyWithDayToEnsureTestIsNotFlaky = new Date();
        expectedDateOnlyWithDayToEnsureTestIsNotFlaky.setDate(expectedDateOnlyWithDayToEnsureTestIsNotFlaky.getDate() + 30);
        expectedDateOnlyWithDayToEnsureTestIsNotFlaky.setHours(0, 0, 0, 0);

        component.remindMeIn30Days();

        const savedDate = localStorageServiceSpy.mock.calls[0][1] as Date;

        const savedDateOnlyWithDay = savedDate;
        savedDateOnlyWithDay.setHours(0, 0, 0, 0);
        expect(savedDateOnlyWithDay.getTime()).toBe(expectedDateOnlyWithDayToEnsureTestIsNotFlaky.getTime());

        expect(localStorageServiceSpy).toHaveBeenCalledWith(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, savedDate);
        expect(component.visible()).toBe(false);
    });

    describe('auto-open on authentication', () => {
        function enablePasskeyFeature() {
            const service = TestBed.inject(ProfileService);
            vi.spyOn(service, 'getProfileInfo').mockReturnValue({ activeModuleFeatures: [MODULE_FEATURE_PASSKEY] } as unknown as ProfileInfo);
        }

        beforeEach(() => {
            // LocalStorageService.clear() preserves the passkey reminder key, so remove it explicitly
            localStorage.removeItem(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY);
        });

        function createComponentAndInit() {
            fixture = TestBed.createComponent(SetupPasskeyModalComponent);
            component = fixture.componentInstance;
            fixture.detectChanges();
        }

        it('should open the modal when passkey feature is enabled and user should set up passkey', () => {
            enablePasskeyFeature();
            TestBed.inject(AccountService).userIdentity.set({ askToSetupPasskey: true } as User);

            createComponentAndInit();

            expect(component.visible()).toBe(true);
        });

        it('should not open the modal when passkey feature is disabled', () => {
            TestBed.inject(AccountService).userIdentity.set({ askToSetupPasskey: true } as User);

            createComponentAndInit();

            expect(component.visible()).toBe(false);
        });

        it('should not open the modal when user does not need to set up passkey', () => {
            enablePasskeyFeature();
            TestBed.inject(AccountService).userIdentity.set({ askToSetupPasskey: false } as User);

            createComponentAndInit();

            expect(component.visible()).toBe(false);
        });

        it('should not open the modal when reminder was set for the future', () => {
            enablePasskeyFeature();
            const futureDate = new Date();
            futureDate.setDate(futureDate.getDate() + 1);
            TestBed.inject(LocalStorageService).store(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, futureDate);
            TestBed.inject(AccountService).userIdentity.set({ askToSetupPasskey: true } as User);

            createComponentAndInit();

            expect(component.visible()).toBe(false);
        });

        it('should open the modal when reminder date is in the past', () => {
            enablePasskeyFeature();
            const dateInPast = new Date();
            dateInPast.setDate(dateInPast.getDate() - 10);
            TestBed.inject(LocalStorageService).store(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, dateInPast);
            TestBed.inject(AccountService).userIdentity.set({ askToSetupPasskey: true } as User);

            createComponentAndInit();

            expect(component.visible()).toBe(true);
        });

        it('should not reopen the modal after "Set up later" when the authentication state emits again', () => {
            enablePasskeyFeature();
            const accountService = TestBed.inject(AccountService);
            accountService.userIdentity.set({ askToSetupPasskey: true } as User);
            const authenticationState = new Subject<User | undefined>();
            vi.spyOn(accountService, 'getAuthenticationState').mockReturnValue(authenticationState.asObservable());

            createComponentAndInit();

            // Initial authentication emission opens the prompt
            authenticationState.next({ askToSetupPasskey: true } as User);
            expect(component.visible()).toBe(true);

            // User chooses "Set up later"
            component.closeModal();
            expect(component.visible()).toBe(false);

            // A later re-emission (e.g. after changing the AI experience) must not reopen it
            authenticationState.next({ askToSetupPasskey: true } as User);
            expect(component.visible()).toBe(false);
        });
    });
});
