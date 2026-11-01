/**
 * Vitest tests for SettingsComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { of, throwError } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { SettingsComponent } from 'app/account/settings/settings.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { User } from 'app/account/user/user.model';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MODULE_FEATURE_SAML2 } from 'app/app.constants';
import { PendingEmailChangeDTO } from 'app/account/user/shared/dto/pending-email-change.dto';

describe('SettingsComponent', () => {
    let comp: SettingsComponent;
    let accountService: AccountService;

    const accountValues: User = {
        internal: true,
        name: 'John Doe',
        firstName: 'John',
        lastName: 'Doe',
        activated: true,
        email: 'john.doe@mail.com',
        langKey: 'en',
        login: 'john',
        authorities: [],
        imageUrl: '',
    };

    const pendingEmailChange: PendingEmailChangeDTO = {
        newEmail: 'new.email@mail.com',
        requestedAt: '2026-10-13T10:00:00Z',
        expiresAt: '2026-10-14T10:00:00Z',
        currentAddressConfirmed: false,
        newAddressConfirmed: true,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [SettingsComponent],
            providers: [
                FormBuilder,
                LocalStorageService,
                SessionStorageService,
                ProfileService,
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideTemplate(SettingsComponent, '')
            .compileComponents();
    });

    beforeEach(() => {
        // Mock ProfileService to return registrationEnabled: true before component creation
        const profileService = TestBed.inject(ProfileService);
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ registrationEnabled: true, mailConfigured: true } as any);
        vi.spyOn(profileService, 'isProfileActive').mockReturnValue(false);
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);

        const fixture = TestBed.createComponent(SettingsComponent);
        comp = fixture.componentInstance;
        accountService = TestBed.inject(AccountService);
    });

    it('should send the current identity upon save', async () => {
        // GIVEN
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(accountValues));
        vi.spyOn(accountService, 'save').mockReturnValue(of({ pendingEmailChange: null }));
        vi.spyOn(accountService, 'authenticate');

        const settingsFormValues = {
            firstName: 'John',
            lastName: 'Doe',
            email: 'john.doe@mail.com',
            langKey: 'en',
        };

        // WHEN
        comp.ngOnInit();
        await vi.waitFor(() => expect(comp.currentUser()).toBeDefined());
        comp.saveSettings();

        // THEN
        expect(accountService.identity).toHaveBeenCalled();
        expect(accountService.save).toHaveBeenCalledWith(accountValues);
        expect(accountService.authenticate).toHaveBeenCalledWith(accountValues);
        expect(comp.settingsForm.value).toEqual(settingsFormValues);
    });

    it('should notify of success upon successful save', async () => {
        // GIVEN
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(accountValues));
        vi.spyOn(accountService, 'save').mockReturnValue(of({ pendingEmailChange: null }));

        // WHEN
        comp.ngOnInit();
        await vi.waitFor(() => expect(comp.currentUser()).toBeDefined());
        comp.saveSettings();

        // THEN
        expect(comp.success()).toBe(true);
    });

    it('should notify of error upon failed save', async () => {
        // GIVEN
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(accountValues));
        vi.spyOn(accountService, 'save').mockReturnValue(throwError(() => new Error('ERROR')));

        // WHEN
        comp.ngOnInit();
        await vi.waitFor(() => expect(comp.currentUser()).toBeDefined());
        comp.saveSettings();

        // THEN
        expect(comp.success()).toBe(false);
    });

    it('should return early from save when account is undefined', () => {
        // GIVEN: currentUser is undefined (ngOnInit not called)
        const saveSpy = vi.spyOn(accountService, 'save');

        // WHEN
        comp.saveSettings();

        // THEN
        expect(saveSpy).not.toHaveBeenCalled();
        expect(comp.success()).toBe(false);
    });

    it('should not update form when identity returns undefined user', async () => {
        // GIVEN
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(undefined));

        // WHEN
        comp.ngOnInit();
        await vi.waitFor(() => Promise.resolve()); // Let promise resolve

        // THEN
        expect(comp.currentUser()).toBeUndefined();
        // Form controls are not patched, so firstName stays at its initial value
        expect(comp.settingsForm.controls.firstName.value).toBeFalsy();
    });

    it('should change language when langKey differs from current language', async () => {
        // GIVEN
        const translateService = TestBed.inject(TranslateService);
        const userWithDifferentLang = { ...accountValues, langKey: 'de' };
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(userWithDifferentLang));
        vi.spyOn(accountService, 'save').mockReturnValue(of({ pendingEmailChange: null }));
        vi.spyOn(translateService, 'getCurrentLang').mockReturnValue('en');
        const useSpy = vi.spyOn(translateService, 'use');

        // WHEN
        comp.ngOnInit();
        await vi.waitFor(() => expect(comp.currentUser()).toBeDefined());
        comp.saveSettings();

        // THEN
        expect(useSpy).toHaveBeenCalledWith('de');
    });

    it('should not change language when langKey matches current language', async () => {
        // GIVEN
        const translateService = TestBed.inject(TranslateService);
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(accountValues));
        vi.spyOn(accountService, 'save').mockReturnValue(of({ pendingEmailChange: null }));
        vi.spyOn(translateService, 'getCurrentLang').mockReturnValue('en'); // Same as accountValues.langKey
        const useSpy = vi.spyOn(translateService, 'use');

        // WHEN
        comp.ngOnInit();
        await vi.waitFor(() => expect(comp.currentUser()).toBeDefined());
        comp.saveSettings();

        // THEN
        expect(useSpy).not.toHaveBeenCalled();
    });

    it('should handle empty form values by setting undefined', async () => {
        // GIVEN
        const userWithEmptyValues = { ...accountValues };
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(userWithEmptyValues));
        vi.spyOn(accountService, 'save').mockReturnValue(of({ pendingEmailChange: null }));

        // WHEN
        comp.ngOnInit();
        await vi.waitFor(() => expect(comp.currentUser()).toBeDefined());

        // Clear form values to trigger || undefined branch
        comp.settingsForm.patchValue({
            firstName: '',
            lastName: '',
            email: '',
            langKey: '',
        });
        comp.saveSettings();

        // THEN
        const sentUser = vi.mocked(accountService.save).mock.calls[0][0];
        expect(sentUser.firstName).toBeUndefined();
        expect(sentUser.lastName).toBeUndefined();
        expect(sentUser.email).toBeUndefined();
        expect(sentUser.langKey).toBeUndefined();
        // The email address of the account is never changed by a save
        expect(comp.currentUser()?.email).toBe(accountValues.email);
    });

    it('should set errorEmailExists when email is already used', async () => {
        // GIVEN
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(accountValues));
        vi.spyOn(accountService, 'save').mockReturnValue(
            throwError(() => ({
                status: 400,
                error: { type: 'https://www.jhipster.tech/problem/email-already-used' },
            })),
        );

        // WHEN
        comp.ngOnInit();
        await vi.waitFor(() => expect(comp.currentUser()).toBeDefined());
        comp.saveSettings();

        // THEN
        expect(comp.errorEmailExists()).toBe(true);
        expect(comp.success()).toBe(false);
    });

    it('should keep the current email and show the pending change after requesting a new email', async () => {
        // GIVEN
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(accountValues));
        vi.spyOn(accountService, 'save').mockReturnValue(of({ pendingEmailChange }));
        vi.spyOn(accountService, 'authenticate');

        comp.ngOnInit();
        await vi.waitFor(() => expect(comp.currentUser()).toBeDefined());

        // WHEN
        comp.settingsForm.patchValue({ email: 'new.email@mail.com', firstName: 'Johnny' });
        comp.saveSettings();

        // THEN the new email is sent to the server
        expect(vi.mocked(accountService.save).mock.calls[0][0].email).toBe('new.email@mail.com');
        // the pending change is shown, the form and the in-memory user keep the current email
        expect(comp.success()).toBe(true);
        expect(comp.emailChangeRequested()).toBe(true);
        expect(comp.pendingEmailChange()).toEqual(pendingEmailChange);
        expect(comp.settingsForm.controls.email.value).toBe('john.doe@mail.com');
        expect(comp.currentUser()?.email).toBe('john.doe@mail.com');
        // other changes are applied right away
        expect(comp.currentUser()?.firstName).toBe('Johnny');
        const authenticatedUser = vi.mocked(accountService.authenticate).mock.calls[0][0];
        expect(authenticatedUser?.email).toBe('john.doe@mail.com');
        expect(authenticatedUser?.firstName).toBe('Johnny');
    });

    it('should not announce confirmation mails when the email was not changed', async () => {
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(accountValues));
        vi.spyOn(accountService, 'save').mockReturnValue(of({ pendingEmailChange }));

        comp.ngOnInit();
        await vi.waitFor(() => expect(comp.currentUser()).toBeDefined());
        comp.saveSettings();

        expect(comp.success()).toBe(true);
        expect(comp.emailChangeRequested()).toBe(false);
        // an earlier request is kept and still shown
        expect(comp.pendingEmailChange()).toEqual(pendingEmailChange);
    });

    it.each(['new.email@mail.com', 'JOHN.DOE@MAIL.COM'])('should not announce new mails when saving %s returns the existing request', async (email) => {
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(accountValues));
        vi.spyOn(accountService, 'getPendingEmailChange').mockReturnValue(of(pendingEmailChange));
        vi.spyOn(accountService, 'save').mockReturnValue(of({ pendingEmailChange }));
        comp.ngOnInit();
        await vi.waitFor(() => expect(comp.pendingEmailChange()).toEqual(pendingEmailChange));

        comp.settingsForm.controls.email.setValue(email);
        comp.saveSettings();

        expect(comp.success()).toBe(true);
        expect(comp.emailChangeRequested()).toBe(false);
        expect(comp.pendingEmailChange()).toEqual(pendingEmailChange);
        expect(comp.currentUser()?.email).toBe('john.doe@mail.com');
    });

    it.each([
        ['2026-10-13T10:00:00.123456789Z', '2026-10-13T10:00:00.123Z'],
        ['2026-10-13T10:00:00.123900000Z', '2026-10-13T10:00:00.124Z'],
    ])('should recognize the same request after database timestamp rounding from %s to %s', async (createdAt, persistedAt) => {
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(accountValues));
        const created = { ...pendingEmailChange, requestedAt: createdAt };
        const persisted = { ...pendingEmailChange, requestedAt: persistedAt };
        vi.spyOn(accountService, 'save')
            .mockReturnValueOnce(of({ pendingEmailChange: created }))
            .mockReturnValueOnce(of({ pendingEmailChange: persisted }));
        comp.ngOnInit();
        await vi.waitFor(() => expect(comp.currentUser()).toBeDefined());

        comp.settingsForm.controls.email.setValue('new.email@mail.com');
        comp.saveSettings();
        expect(comp.emailChangeRequested()).toBe(true);
        comp.settingsForm.controls.email.setValue('new.email@mail.com');
        comp.saveSettings();

        expect(comp.emailChangeRequested()).toBe(false);
        expect(comp.pendingEmailChange()).toEqual(persisted);
    });

    it('should announce mails when a save replaces the pending request', async () => {
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(accountValues));
        vi.spyOn(accountService, 'getPendingEmailChange').mockReturnValue(of(pendingEmailChange));
        const replacement = { ...pendingEmailChange, newEmail: 'another.email@mail.com', requestedAt: '2026-10-13T11:00:00Z' };
        vi.spyOn(accountService, 'save').mockReturnValue(of({ pendingEmailChange: replacement }));
        comp.ngOnInit();
        await vi.waitFor(() => expect(comp.pendingEmailChange()).toEqual(pendingEmailChange));

        comp.settingsForm.controls.email.setValue('another.email@mail.com');
        comp.saveSettings();

        expect(comp.emailChangeRequested()).toBe(true);
        expect(comp.pendingEmailChange()).toEqual(replacement);
    });

    it('should load the pending email change on init', async () => {
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(accountValues));
        const getSpy = vi.spyOn(accountService, 'getPendingEmailChange').mockReturnValue(of(pendingEmailChange));

        comp.ngOnInit();
        await vi.waitFor(() => expect(comp.pendingEmailChange()).toEqual(pendingEmailChange));

        expect(getSpy).toHaveBeenCalled();
    });

    it('should not load a pending email change for a non-internal user', async () => {
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve({ ...accountValues, internal: false }));
        const getSpy = vi.spyOn(accountService, 'getPendingEmailChange');

        comp.ngOnInit();
        await vi.waitFor(() => expect(comp.currentUser()).toBeDefined());

        expect(getSpy).not.toHaveBeenCalled();
    });

    it('should resend the confirmation mails and show the restarted request', async () => {
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(accountValues));
        vi.spyOn(accountService, 'getPendingEmailChange').mockReturnValue(of(pendingEmailChange));
        const restarted = { ...pendingEmailChange, newAddressConfirmed: false, expiresAt: '2026-10-15T10:00:00Z' };
        const resendSpy = vi.spyOn(accountService, 'resendPendingEmailChange').mockReturnValue(of(restarted));
        comp.ngOnInit();
        await vi.waitFor(() => expect(comp.pendingEmailChange()).toBeDefined());

        comp.resendConfirmationMails();

        expect(resendSpy).toHaveBeenCalledOnce();
        expect(comp.pendingEmailChange()).toEqual(restarted);
        expect(comp.confirmationMailsResent()).toBe(true);
        expect(comp.pendingChangeRequestRunning()).toBe(false);
    });

    it('should show the error when resending too soon', () => {
        vi.spyOn(accountService, 'resendPendingEmailChange').mockReturnValue(throwError(() => ({ status: 400, error: { errorKey: 'emailChangeRequestedTooRecently' } })));

        comp.resendConfirmationMails();

        expect(comp.errorMessageKey()).toBe('settings.messages.error.emailChangeRequestedTooRecently');
        expect(comp.confirmationMailsResent()).toBe(false);
        expect(comp.pendingChangeRequestRunning()).toBe(false);
    });

    it('should cancel the pending email change', async () => {
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(accountValues));
        vi.spyOn(accountService, 'getPendingEmailChange').mockReturnValue(of(pendingEmailChange));
        const cancelSpy = vi.spyOn(accountService, 'cancelPendingEmailChange').mockReturnValue(of(undefined));
        comp.ngOnInit();
        await vi.waitFor(() => expect(comp.pendingEmailChange()).toBeDefined());

        comp.cancelEmailChange();

        expect(cancelSpy).toHaveBeenCalledOnce();
        expect(comp.pendingEmailChange()).toBeUndefined();
        expect(comp.emailChangeCancelled()).toBe(true);
    });

    it('should keep the pending email change when cancelling fails', async () => {
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(accountValues));
        vi.spyOn(accountService, 'getPendingEmailChange').mockReturnValue(of(pendingEmailChange));
        vi.spyOn(accountService, 'cancelPendingEmailChange').mockReturnValue(throwError(() => ({ status: 500 })));
        comp.ngOnInit();
        await vi.waitFor(() => expect(comp.pendingEmailChange()).toBeDefined());

        comp.cancelEmailChange();

        expect(comp.pendingEmailChange()).toEqual(pendingEmailChange);
        expect(comp.errorMessageKey()).toBe('settings.messages.error.fail');
    });

    it.each(['emailInvalid', 'emailChangeUnavailable', 'emailChangeRequestedTooRecently', 'noPendingEmailChange'])(
        'should map the server error %s to its message',
        async (errorKey) => {
            vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(accountValues));
            vi.spyOn(accountService, 'save').mockReturnValue(throwError(() => ({ status: 400, error: { errorKey } })));
            comp.ngOnInit();
            await vi.waitFor(() => expect(comp.currentUser()).toBeDefined());

            comp.saveSettings();

            expect(comp.errorMessageKey()).toBe(`settings.messages.error.${errorKey}`);
            expect(comp.success()).toBe(false);
        },
    );

    it('should map the emailAlreadyUsed error key to the email exists message', async () => {
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(accountValues));
        vi.spyOn(accountService, 'save').mockReturnValue(throwError(() => ({ status: 400, error: { errorKey: 'emailAlreadyUsed' } })));
        comp.ngOnInit();
        await vi.waitFor(() => expect(comp.currentUser()).toBeDefined());

        comp.saveSettings();

        expect(comp.errorEmailExists()).toBe(true);
        expect(comp.errorMessageKey()).toBeUndefined();
    });

    it('should show a generic message for an unknown failure', async () => {
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(accountValues));
        vi.spyOn(accountService, 'save').mockReturnValue(throwError(() => ({ status: 429 })));
        comp.ngOnInit();
        await vi.waitFor(() => expect(comp.currentUser()).toBeDefined());

        comp.saveSettings();

        expect(comp.errorMessageKey()).toBe('settings.messages.error.fail');
    });

    describe('when the server cannot send mails', () => {
        let compNoMail: SettingsComponent;

        beforeEach(() => {
            const profileService = TestBed.inject(ProfileService);
            vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ registrationEnabled: true, mailConfigured: false } as any);

            compNoMail = TestBed.createComponent(SettingsComponent).componentInstance;
        });

        it('should disable the email field but keep the name editable', async () => {
            vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(accountValues));

            compNoMail.ngOnInit();
            await vi.waitFor(() => expect(compNoMail.currentUser()).toBeDefined());

            expect(compNoMail.mailConfigured()).toBe(false);
            expect(compNoMail.settingsForm.controls.email.disabled).toBe(true);
            expect(compNoMail.settingsForm.controls.firstName.enabled).toBe(true);
            expect(compNoMail.settingsForm.controls.langKey.enabled).toBe(true);
        });
    });

    describe('when registration is disabled', () => {
        let compDisabled: SettingsComponent;

        beforeEach(() => {
            // Override ProfileService mock to return registrationEnabled: false
            const profileService = TestBed.inject(ProfileService);
            vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ registrationEnabled: false } as any);

            const fixture = TestBed.createComponent(SettingsComponent);
            compDisabled = fixture.componentInstance;
        });

        it('should set isRegistrationEnabled to false when profile has registrationEnabled false', () => {
            expect(compDisabled.isRegistrationEnabled()).toBe(false);
        });
    });

    describe('when email pattern is configured', () => {
        let compWithPattern: SettingsComponent;

        beforeEach(() => {
            // Override ProfileService mock to return email pattern
            const profileService = TestBed.inject(ProfileService);
            vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({
                registrationEnabled: true,
                mailConfigured: true,
                allowedEmailPattern: '^.*@university\\.edu$',
                allowedEmailPatternReadable: '@university.edu',
            } as any);

            const fixture = TestBed.createComponent(SettingsComponent);
            compWithPattern = fixture.componentInstance;
        });

        it('should use email pattern validation when configured', () => {
            expect(compWithPattern.allowedEmailPattern).toBe('^.*@university\\.edu$');
            expect(compWithPattern.allowedEmailPatternReadable()).toBe('@university.edu');

            // Email that matches pattern should be valid
            compWithPattern.settingsForm.patchValue({ email: 'test@university.edu' });
            expect(compWithPattern.settingsForm.controls.email.valid).toBe(true);

            // Email that doesn't match pattern should be invalid
            compWithPattern.settingsForm.patchValue({ email: 'test@other.com' });
            expect(compWithPattern.settingsForm.controls.email.valid).toBe(false);
            expect(compWithPattern.settingsForm.controls.email.errors?.['pattern']).toBeTruthy();
        });
    });

    describe('when registration is undefined', () => {
        let compUndefined: SettingsComponent;

        beforeEach(() => {
            // Override ProfileService mock to return registrationEnabled: undefined
            const profileService = TestBed.inject(ProfileService);
            vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({} as any);

            const fixture = TestBed.createComponent(SettingsComponent);
            compUndefined = fixture.componentInstance;
        });

        it('should default isRegistrationEnabled to false when profile has registrationEnabled undefined', () => {
            expect(compUndefined.isRegistrationEnabled()).toBe(false);
        });
    });

    describe('when SAML2 profile is active', () => {
        let compSaml2: SettingsComponent;
        let accountServiceSaml2: AccountService;

        beforeEach(() => {
            const profileService = TestBed.inject(ProfileService);
            vi.spyOn(profileService, 'isModuleFeatureActive').mockImplementation((feature) => feature === MODULE_FEATURE_SAML2);

            const fixture = TestBed.createComponent(SettingsComponent);
            compSaml2 = fixture.componentInstance;
            accountServiceSaml2 = TestBed.inject(AccountService);
        });

        it('should set isSaml2Active to true', () => {
            expect(compSaml2.isSaml2Active()).toBe(true);
        });

        it('should disable firstName, lastName and email form controls after loading user', async () => {
            const user: User = {
                internal: true,
                name: 'Jane Smith',
                firstName: 'Jane',
                lastName: 'Smith',
                activated: true,
                email: 'jane.smith@idp.example.com',
                langKey: 'en',
                login: 'jsmith',
                authorities: [],
                imageUrl: '',
            };
            vi.spyOn(accountServiceSaml2, 'identity').mockReturnValue(Promise.resolve(user));

            compSaml2.ngOnInit();
            await vi.waitFor(() => expect(compSaml2.currentUser()).toBeDefined());

            expect(compSaml2.settingsForm.controls.firstName.disabled).toBe(true);
            expect(compSaml2.settingsForm.controls.lastName.disabled).toBe(true);
            expect(compSaml2.settingsForm.controls.email.disabled).toBe(true);
        });

        it('should not update firstName, lastName, or email when saving with SAML2 active', async () => {
            const user: User = {
                internal: true,
                name: 'Jane Smith',
                firstName: 'Jane',
                lastName: 'Smith',
                activated: true,
                email: 'jane.smith@idp.example.com',
                langKey: 'en',
                login: 'jsmith',
                authorities: [],
                imageUrl: '',
            };
            vi.spyOn(accountServiceSaml2, 'identity').mockReturnValue(Promise.resolve(user));
            vi.spyOn(accountServiceSaml2, 'save').mockReturnValue(of({ pendingEmailChange: null }));
            vi.spyOn(accountServiceSaml2, 'authenticate');

            compSaml2.ngOnInit();
            await vi.waitFor(() => expect(compSaml2.currentUser()).toBeDefined());

            // Try to change first name via form — should be ignored
            compSaml2.settingsForm.patchValue({ firstName: 'Hacker', lastName: 'Attack', email: 'hacked@evil.com', langKey: 'de' });
            compSaml2.saveSettings();

            const savedUser = compSaml2.currentUser();
            // Name and email should remain unchanged
            expect(savedUser?.firstName).toBe('Jane');
            expect(savedUser?.lastName).toBe('Smith');
            expect(savedUser?.email).toBe('jane.smith@idp.example.com');
            // Only langKey should be updated
            expect(savedUser?.langKey).toBe('de');
        });
    });
});
