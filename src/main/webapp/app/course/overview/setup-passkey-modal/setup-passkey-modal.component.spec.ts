import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective, MockProvider } from 'ng-mocks';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, SetupPasskeyModalComponent } from './setup-passkey-modal.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AlertService } from 'app/foundation/service/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/account/user/user.model';
import { MODULE_FEATURE_PASSKEY } from 'app/app.constants';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

describe('SetupPasskeyModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: SetupPasskeyModalComponent;
    let fixture: ComponentFixture<SetupPasskeyModalComponent>;
    let localStorageService: LocalStorageService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [SetupPasskeyModalComponent, MockDirective(TranslateDirective)],
            providers: [
                MockProvider(AlertService),
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: AccountService, useClass: MockAccountService },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

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
    });
});
