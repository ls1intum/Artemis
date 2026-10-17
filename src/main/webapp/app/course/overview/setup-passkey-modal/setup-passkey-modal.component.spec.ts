import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Subject, firstValueFrom } from 'rxjs';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { SetupPasskeyModalComponent } from './setup-passkey-modal.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AlertService } from 'app/foundation/service/alert.service';
import { AccountService } from 'app/core/auth/account.service';
import { EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, LocalStorageService } from 'app/foundation/service/local-storage.service';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { User } from 'app/account/user/user.model';
import { MODULE_FEATURE_PASSKEY } from 'app/app.constants';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { TumAetUiButtonComponent } from '@tumaet/ui-angular';
import { AuthServerProvider } from 'app/core/auth/auth-jwt.service';

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

    describe('UI rendering and footer controls', () => {
        beforeEach(() => {
            const profileService = TestBed.inject(ProfileService);
            vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ activeModuleFeatures: [MODULE_FEATURE_PASSKEY] } as unknown as ProfileInfo);
            TestBed.inject(AccountService).userIdentity.set({ askToSetupPasskey: true } as User);
        });

        it('should render the dialog and footer buttons when visible', () => {
            component.visible.set(true);
            fixture.detectChanges();

            expect(fixture.nativeElement.querySelector('tumaet-ui-dialog')).toBeTruthy();
            const buttons = fixture.debugElement.queryAll(By.directive(TumAetUiButtonComponent));
            expect(buttons).toHaveLength(3);
        });

        it('should trigger remindMeIn30Days when clicking the remind button', () => {
            const spy = vi.spyOn(component, 'remindMeIn30Days');
            component.visible.set(true);
            fixture.detectChanges();

            const buttons = fixture.debugElement.queryAll(By.directive(TumAetUiButtonComponent));
            buttons[0].triggerEventHandler('clicked', null);

            expect(spy).toHaveBeenCalledOnce();
        });

        it('should trigger closeModal when clicking the "Set up later" button', () => {
            const spy = vi.spyOn(component, 'closeModal');
            component.visible.set(true);
            fixture.detectChanges();

            const buttons = fixture.debugElement.queryAll(By.directive(TumAetUiButtonComponent));
            buttons[1].triggerEventHandler('clicked', null);

            expect(spy).toHaveBeenCalledOnce();
        });

        it('should trigger setupPasskey when clicking the "Set up passkey" button', () => {
            const spy = vi.spyOn(component, 'setupPasskey').mockResolvedValue();
            component.visible.set(true);
            fixture.detectChanges();

            const buttons = fixture.debugElement.queryAll(By.directive(TumAetUiButtonComponent));
            buttons[2].triggerEventHandler('clicked', null);

            expect(spy).toHaveBeenCalledOnce();
        });
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

/**
 * Round trip through the real {@link LocalStorageService} and the browser's localStorage. A page reload is simulated by throwing away the
 * whole testing module (and with it the component and every service) while the storage survives, which is what a refresh does.
 * The specs above exercise the component and the storage service separately, with the storage replaced by a map.
 */
describe('SetupPasskeyModalComponent reminder across page reloads', () => {
    let authenticationState: Subject<User | undefined>;

    beforeEach(() => {
        vi.unstubAllGlobals();
        localStorage.clear();
        sessionStorage.clear();
    });

    afterEach(() => {
        vi.useRealTimers();
        localStorage.clear();
        sessionStorage.clear();
    });

    /** Boots a fresh "page": new injector, new component, and the authentication state of a logged-in user who has not set up a passkey yet. */
    async function loadPage() {
        TestBed.resetTestingModule();
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

        vi.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({ activeModuleFeatures: [MODULE_FEATURE_PASSKEY] } as unknown as ProfileInfo);
        const accountService = TestBed.inject(AccountService);
        accountService.userIdentity.set({ askToSetupPasskey: true } as User);
        authenticationState = new Subject<User | undefined>();
        vi.spyOn(accountService, 'getAuthenticationState').mockReturnValue(authenticationState.asObservable());

        const fixture = TestBed.createComponent(SetupPasskeyModalComponent);
        fixture.detectChanges();
        // the application announces the logged-in user once the account has been loaded
        authenticationState.next({ askToSetupPasskey: true } as User);
        return fixture.componentInstance;
    }

    it('should ask again on a page without a stored reminder', async () => {
        const component = await loadPage();

        expect(component.visible()).toBe(true);
    });

    it('should stay closed after a reload when the user chose "Remind me in 30 days"', async () => {
        const firstPage = await loadPage();
        expect(firstPage.visible()).toBe(true);

        firstPage.remindMeIn30Days();
        expect(firstPage.visible()).toBe(false);

        const reloadedPage = await loadPage();
        expect(reloadedPage.visible()).toBe(false);

        // and again on every further reload
        const reloadedAgain = await loadPage();
        expect(reloadedAgain.visible()).toBe(false);
    });

    it('should stay closed after the user logged out and back in again', async () => {
        const firstPage = await loadPage();
        firstPage.remindMeIn30Days();

        // logging out clears the caches, which must keep the reminder
        await firstValueFrom(TestBed.inject(AuthServerProvider).clearCaches());
        expect(localStorage.getItem(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY)).not.toBeNull();

        const pageAfterLogin = await loadPage();
        expect(pageAfterLogin.visible()).toBe(false);
    });

    it('should not store the reminder when the user only chose "Set up later"', async () => {
        const firstPage = await loadPage();
        firstPage.closeModal();
        expect(localStorage.getItem(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY)).toBeNull();

        const reloadedPage = await loadPage();
        expect(reloadedPage.visible()).toBe(true);
    });

    it('should ask again once the 30 days are over', async () => {
        vi.useFakeTimers({ toFake: ['Date'] });
        vi.setSystemTime(new Date('2026-06-18T10:00:00.000Z'));
        const firstPage = await loadPage();
        firstPage.remindMeIn30Days();

        vi.setSystemTime(new Date('2026-07-17T10:00:00.000Z'));
        expect((await loadPage()).visible()).toBe(false);

        vi.setSystemTime(new Date('2026-07-19T10:00:00.000Z'));
        expect((await loadPage()).visible()).toBe(true);
    });

    it('should keep the reminder independent of the time zone offset in the stored value', async () => {
        // a date that is already past must not suppress the prompt, whatever the offset of the browser is
        localStorage.setItem(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, JSON.stringify(new Date(Date.now() - 1000)));
        expect((await loadPage()).visible()).toBe(true);

        localStorage.setItem(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, JSON.stringify(new Date(Date.now() + 24 * 60 * 60 * 1000)));
        expect((await loadPage()).visible()).toBe(false);
    });
});
