import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiDialogComponent } from '@tumaet/ui-angular/dialog';
import { of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AccountSettingsDialogComponent } from 'app/account/settings/account-settings-dialog.component';
import { User } from 'app/account/user/user.model';
import { PendingEmailChangeDTO } from 'app/account/user/shared/dto/pending-email-change.dto';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { EMAIL_ALLOWED_DEBOUNCE_MS, EMAIL_ALLOWED_TIMEOUT_MS, EMAIL_ALLOWED_URL } from 'app/account/shared/email-allowed';
import { SKIP_HTTP_ERROR_ALERT } from 'app/core/interceptor/errorhandler.interceptor';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('AccountSettingsDialogComponent', () => {
    let fixture: ComponentFixture<AccountSettingsDialogComponent>;
    let component: AccountSettingsDialogComponent;
    let userIdentity: ReturnType<typeof signal<User | undefined>>;
    let accountService: { userIdentity: typeof userIdentity; save: ReturnType<typeof vi.fn>; authenticate: ReturnType<typeof vi.fn> };
    let profileService: { getProfileInfo: ReturnType<typeof vi.fn>; isModuleFeatureActive: ReturnType<typeof vi.fn> };

    const user: User = {
        internal: true,
        name: 'Ada Lovelace',
        firstName: 'Ada',
        lastName: 'Lovelace',
        email: 'ada@example.com',
        langKey: 'en',
        login: 'ada',
        authorities: [],
    };

    beforeEach(async () => {
        userIdentity = signal<User | undefined>({ ...user });
        accountService = {
            userIdentity,
            save: vi.fn().mockReturnValue(of(new HttpResponse<User>({ body: userIdentity() }))),
            authenticate: vi.fn(),
        };
        profileService = {
            getProfileInfo: vi.fn().mockReturnValue({}),
            isModuleFeatureActive: vi.fn().mockReturnValue(false),
        };

        await TestBed.configureTestingModule({
            imports: [AccountSettingsDialogComponent],
            providers: [
                { provide: AccountService, useValue: accountService },
                { provide: ProfileService, useValue: profileService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();
    });

    function createComponent(): void {
        fixture = TestBed.createComponent(AccountSettingsDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    }

    it('uses the AET dialog and does not offer a language control', () => {
        createComponent();

        expect(fixture.debugElement.query(By.directive(TumAetUiDialogComponent))).toBeTruthy();
        expect('langKey' in component.settingsModel()).toBe(false);
        expect(fixture.nativeElement.querySelector('[formControlName="langKey"]')).toBeNull();
    });

    it('loads the current account when opened', () => {
        createComponent();

        component.initializeForm();

        expect(component.settingsModel()).toEqual({ firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com' });
    });

    it('only enables saving while valid account settings differ from the current account', () => {
        createComponent();
        component.initializeForm();
        component.visible.set(true);
        fixture.detectChanges();

        const saveButton = document.querySelector<HTMLButtonElement>('[data-testid="account-settings-save-button"]')!;
        expect(saveButton.disabled).toBe(true);

        component.settingsForm.firstName().value.set('Grace');
        fixture.detectChanges();
        expect(saveButton.disabled).toBe(false);

        component.settingsForm.firstName().value.set('Ada');
        fixture.detectChanges();
        expect(saveButton.disabled).toBe(true);
    });

    it('saves through native form submission and prevents navigation', () => {
        createComponent();
        component.initializeForm();
        component.visible.set(true);
        fixture.detectChanges();
        component.settingsForm.firstName().value.set('Grace');
        fixture.detectChanges();
        const form = document.querySelector<HTMLFormElement>('#account-settings-form')!;
        const submitEvent = new SubmitEvent('submit', { bubbles: true, cancelable: true });

        const notCancelled = form.dispatchEvent(submitEvent);

        expect(notCancelled).toBe(false);
        expect(accountService.save).toHaveBeenCalledWith(expect.objectContaining({ firstName: 'Grace' }));
    });

    it('saves name and email, updates the authenticated account, and closes the dialog', () => {
        createComponent();
        component.initializeForm();
        component.visible.set(true);
        component.settingsModel.set({ firstName: 'Grace', lastName: 'Hopper', email: 'grace@example.com' });

        component.saveSettings();

        expect(accountService.save).toHaveBeenCalledWith(expect.objectContaining({ firstName: 'Grace', lastName: 'Hopper', email: 'grace@example.com', langKey: 'en' }));
        // A changed email address is not applied: the account keeps its current address until the change is confirmed
        expect(accountService.authenticate).toHaveBeenCalledWith(
            expect.objectContaining({ name: 'Grace Hopper', firstName: 'Grace', lastName: 'Hopper', email: 'ada@example.com' }),
        );
        expect(userIdentity()).toEqual(expect.objectContaining({ firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com' }));
        expect(component.visible()).toBe(false);
    });

    it('only derives a combined name longer than 50 characters after saving', () => {
        const firstName = 'A'.repeat(30);
        const lastName = 'B'.repeat(30);
        let requestName: string | undefined;
        accountService.save.mockImplementation((userToSave: User) => {
            requestName = userToSave.name;
            return of(new HttpResponse<User>({ body: userToSave }));
        });
        createComponent();
        component.initializeForm();
        component.settingsModel.set({ firstName, lastName, email: 'ada@example.com' });

        component.saveSettings();

        expect(requestName).toBe('Ada Lovelace');
        expect(accountService.authenticate).toHaveBeenCalledWith(expect.objectContaining({ name: `${firstName} ${lastName}`, firstName, lastName }));
    });

    it('keeps the dialog open when saving fails', () => {
        accountService.save.mockReturnValue(throwError(() => new Error('Save failed')));
        createComponent();
        component.initializeForm();
        component.visible.set(true);
        component.settingsModel.set({ firstName: 'Grace', lastName: 'Hopper', email: 'grace@example.com' });

        component.saveSettings();

        expect(userIdentity()).toEqual(expect.objectContaining({ firstName: 'Ada', lastName: 'Lovelace', email: 'ada@example.com' }));
        expect(component.visible()).toBe(true);
    });

    describe('email restriction', () => {
        let http: HttpTestingController;

        beforeEach(() => {
            vi.useFakeTimers();
            http = TestBed.inject(HttpTestingController);
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        /** Lets the debounce of the check run out and the form react to the answer. */
        async function pause(): Promise<void> {
            await vi.advanceTimersByTimeAsync(EMAIL_ALLOWED_DEBOUNCE_MS + 50);
            TestBed.tick();
        }

        async function typeEmail(value: string): Promise<void> {
            component.settingsForm.email().value.set(value);
            await pause();
        }

        function restrictedTo(readable = '@example.com'): void {
            profileService.getProfileInfo.mockReturnValue({ allowedEmailPattern: '[a-z]+@example\\.com', allowedEmailPatternReadable: readable });
            createComponent();
        }

        it('asks the server once typing has paused, and not for every key', async () => {
            restrictedTo();
            component.settingsForm.email().value.set('g@example.com');
            await vi.advanceTimersByTimeAsync(EMAIL_ALLOWED_DEBOUNCE_MS / 2);
            component.settingsForm.email().value.set('grace@example.com');
            await pause();

            const request = http.expectOne(EMAIL_ALLOWED_URL);
            expect(request.request.method).toBe('POST');
            expect(request.request.body).toEqual({ email: 'grace@example.com' });
            http.verify();
        });

        it('refuses an address that the server does not allow', async () => {
            restrictedTo();
            await typeEmail('ada@other.org');
            http.expectOne(EMAIL_ALLOWED_URL).flush({ allowed: false });
            await pause();

            expect(component.settingsForm.email().valid()).toBe(false);
            expect(component.settingsForm.email().getError('emailNotAllowed')).toBeTruthy();
        });

        it('bounds the email check so an unresponsive server cannot keep saving disabled', async () => {
            restrictedTo();
            await typeEmail('grace@example.com');

            const request = http.expectOne(EMAIL_ALLOWED_URL);
            expect(request.request.timeout).toBe(EMAIL_ALLOWED_TIMEOUT_MS);
            request.flush({ allowed: true });
        });

        it('accepts an address that the server allows, whatever the pattern looks like', async () => {
            restrictedTo();
            await typeEmail('Grace@EXAMPLE.com');
            http.expectOne(EMAIL_ALLOWED_URL).flush({ allowed: true });
            await pause();

            expect(component.settingsForm.email().valid()).toBe(true);
        });

        it('does not block the form when the server cannot answer, because the decision is made again on saving', async () => {
            restrictedTo();
            await typeEmail('grace@example.com');
            http.expectOne(EMAIL_ALLOWED_URL).flush('Too many requests', { status: 429, statusText: 'Too Many Requests' });
            await pause();

            expect(component.settingsForm.email().valid()).toBe(true);
        });

        it('leaves the judgement of the address to the server, also for an address that the browser would not accept', async () => {
            // the server accepts non-ASCII addresses, which the format check of Angular refuses
            restrictedTo();
            await typeEmail('Jörg.Müller@example.com');
            http.expectOne(EMAIL_ALLOWED_URL).flush({ allowed: true });
            await pause();

            expect(component.settingsForm.email().valid()).toBe(true);
        });

        it('does not ask the server about an address that is too short to be one', async () => {
            restrictedTo();
            await typeEmail('a@b');

            http.expectNone(EMAIL_ALLOWED_URL);
            expect(component.settingsForm.email().valid()).toBe(false);
        });

        it('does not show the alert for failed requests when the check fails', async () => {
            restrictedTo();
            await typeEmail('ada@other.org');

            const request = http.expectOne(EMAIL_ALLOWED_URL);
            expect(request.request.context.get(SKIP_HTTP_ERROR_ALERT)).toBe(true);
            request.flush('Server error', { status: 500, statusText: 'Internal Server Error' });
        });

        it('does not ask about the address the account already has, so that the name can be saved by someone whose address predates the restriction', async () => {
            restrictedTo();
            await typeEmail('Ada@Example.COM');

            http.expectNone(EMAIL_ALLOWED_URL);
            expect(component.settingsForm.email().valid()).toBe(true);
            await typeEmail('grace@example.com');
            http.expectOne(EMAIL_ALLOWED_URL).flush({ allowed: true });
        });

        it('names the allowed addresses in the message, because the hint of the field is hidden while the field shows an error', async () => {
            restrictedTo('@example.com');
            component.visible.set(true);
            fixture.detectChanges();
            await typeEmail('ada@other.org');
            http.expectOne(EMAIL_ALLOWED_URL).flush({ allowed: false });
            await pause();
            component.settingsForm.email().markAsTouched();
            fixture.detectChanges();

            const messages = Array.from(document.querySelectorAll('[role="dialog"] small')).map((element) => element.textContent);
            expect(messages).toContain('settings.messages.validate.email.notAllowed');
            expect(messages).toContain('settings.messages.validate.email.hint');
        });

        it('does not ask the server when no restriction is configured', async () => {
            createComponent();
            await typeEmail('anyone@anywhere.org');

            http.expectNone(EMAIL_ALLOWED_URL);
            expect(component.settingsForm.email().valid()).toBe(true);
            await typeEmail('not-an-address');
            expect(component.settingsForm.email().valid()).toBe(false);
        });
    });

    it('tells people which email addresses are allowed before they save, whether or not the browser can check the pattern', () => {
        profileService.getProfileInfo.mockReturnValue({ allowedEmailPatternReadable: '@tum.de, @in.tum.de' });
        createComponent();
        component.visible.set(true);
        fixture.detectChanges();

        const hint = document.querySelector('.tumaet-ui-form-field-hint');
        expect(component.allowedEmailPatternHint).toBe('@tum.de, @in.tum.de');
        // the test translation returns the key, so the interpolated description is covered by the property above
        expect(hint?.textContent).toContain('settings.messages.validate.email.hint');
    });

    it('does not show a hint without a description of the allowed addresses, in particular not the raw pattern', () => {
        profileService.getProfileInfo.mockReturnValue({ allowedEmailPattern: '.*@example\\.com' });
        createComponent();
        component.visible.set(true);
        fixture.detectChanges();

        expect(component.allowedEmailPatternHint).toBeUndefined();
        expect(document.querySelector('.tumaet-ui-form-field-hint')).toBeNull();
    });

    it('keeps identity-managed fields disabled for SAML2 users', () => {
        profileService.isModuleFeatureActive.mockReturnValue(true);
        createComponent();
        component.initializeForm();

        expect(component.settingsForm.firstName().disabled()).toBe(true);
        expect(component.settingsForm.lastName().disabled()).toBe(true);
        expect(component.settingsForm.email().disabled()).toBe(true);

        component.saveSettings();
        expect(accountService.save).not.toHaveBeenCalled();
    });

    describe('email change', () => {
        const pendingEmailChange: PendingEmailChangeDTO = {
            newEmail: 'grace@example.com',
            requestedAt: '2026-10-13T10:00:00Z',
            expiresAt: '2026-10-14T10:00:00Z',
            currentAddressConfirmed: false,
            newAddressConfirmed: false,
        };

        function saveNewEmail(): PendingEmailChangeDTO[] {
            const emitted: PendingEmailChangeDTO[] = [];
            component.emailChangeRequested.subscribe((pending) => emitted.push(pending));
            component.initializeForm();
            component.visible.set(true);
            component.settingsModel.set({ firstName: 'Ada', lastName: 'Lovelace', email: 'grace@example.com' });
            component.saveSettings();
            return emitted;
        }

        it('announces the request that a changed email address created and keeps the current address', () => {
            accountService.save.mockReturnValue(of({ pendingEmailChange }));
            createComponent();

            const emitted = saveNewEmail();

            expect(emitted).toEqual([pendingEmailChange]);
            expect(accountService.authenticate).toHaveBeenCalledWith(expect.objectContaining({ email: 'ada@example.com' }));
            expect(component.visible()).toBe(false);
        });

        it('does not announce confirmation mails when the email address was not changed', () => {
            accountService.save.mockReturnValue(of({ pendingEmailChange }));
            createComponent();
            const emitted: PendingEmailChangeDTO[] = [];
            component.emailChangeRequested.subscribe((pending) => emitted.push(pending));
            component.initializeForm();
            component.settingsModel.set({ firstName: 'Grace', lastName: 'Lovelace', email: 'ada@example.com' });

            component.saveSettings();

            expect(emitted).toEqual([]);
        });

        it('announces mails when a save replaces the pending request with a later one', () => {
            accountService.save.mockReturnValue(of({ pendingEmailChange: { ...pendingEmailChange, requestedAt: '2026-10-13T10:05:00Z' } }));
            createComponent();
            fixture.componentRef.setInput('pendingEmailChange', pendingEmailChange);

            const emitted = saveNewEmail();

            expect(emitted).toHaveLength(1);
        });

        it('does not announce mails again when the server returns the same pending request', () => {
            accountService.save.mockReturnValue(of({ pendingEmailChange }));
            createComponent();
            fixture.componentRef.setInput('pendingEmailChange', pendingEmailChange);

            const emitted = saveNewEmail();

            expect(emitted).toEqual([]);
        });

        it('keeps the email field editable and shows no hint when mails are configured', () => {
            profileService.getProfileInfo.mockReturnValue({ mailConfigured: true });
            createComponent();
            component.initializeForm();
            component.visible.set(true);
            fixture.detectChanges();

            expect(component.settingsForm.email().disabled()).toBe(false);
            expect(document.querySelector('[data-testid="settings-mail-not-configured-hint"]')).toBeNull();
        });

        it('disables the email field but keeps the name editable and shows a hint when the server cannot send mails', () => {
            profileService.getProfileInfo.mockReturnValue({ mailConfigured: false });
            createComponent();
            component.initializeForm();
            component.visible.set(true);
            fixture.detectChanges();

            expect(component.settingsForm.email().disabled()).toBe(true);
            expect(component.settingsForm.firstName().disabled()).toBe(false);
            expect(document.querySelector('[data-testid="settings-mail-not-configured-hint"]')).not.toBeNull();
        });

        it.each([
            { name: 'the jhipster problem type', error: { type: 'https://www.jhipster.tech/problem/email-already-used' } },
            { name: 'the error key', error: { errorKey: 'emailAlreadyUsed' } },
        ])('shows that the email address is in use for $name', ({ error }) => {
            accountService.save.mockReturnValue(throwError(() => ({ status: 400, error })));
            createComponent();

            saveNewEmail();

            expect(component.errorEmailExists()).toBe(true);
            expect(component.errorMessageKey()).toBeUndefined();
            expect(component.visible()).toBe(true);
        });

        it('shows the message of a known email change error', () => {
            accountService.save.mockReturnValue(throwError(() => ({ status: 400, error: { errorKey: 'emailChangeRequestedTooRecently' } })));
            createComponent();

            saveNewEmail();

            expect(component.errorMessageKey()).toBe('settings.messages.error.emailChangeRequestedTooRecently');
        });

        it('shows the message of the daily limit of email change requests', () => {
            accountService.save.mockReturnValue(throwError(() => ({ status: 400, error: { errorKey: 'emailChangeDailyLimitReached' } })));
            createComponent();

            saveNewEmail();

            expect(component.errorMessageKey()).toBe('settings.messages.error.emailChangeDailyLimitReached');
        });

        it('shows a generic message for an unknown failure and clears it when the dialog opens again', () => {
            accountService.save.mockReturnValue(throwError(() => ({ status: 500, error: { errorKey: 'somethingElse' } })));
            createComponent();

            saveNewEmail();
            expect(component.errorMessageKey()).toBe('settings.messages.error.fail');

            component.initializeForm();
            expect(component.errorMessageKey()).toBeUndefined();
        });
    });
});
