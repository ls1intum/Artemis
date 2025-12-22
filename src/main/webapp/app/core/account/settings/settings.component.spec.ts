/**
 * Vitest tests for SettingsComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { FormBuilder } from '@angular/forms';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of, throwError } from 'rxjs';
import { AccountService } from 'app/core/auth/account.service';
import { SettingsComponent } from 'app/core/account/settings/settings.component';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { User } from 'app/core/user/user.model';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { HttpResponse } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('SettingsComponent', () => {
    setupTestBed({ zoneless: true });

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
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ registrationEnabled: true } as any);

        const fixture = TestBed.createComponent(SettingsComponent);
        comp = fixture.componentInstance;
        accountService = TestBed.inject(AccountService);
    });

    it('should send the current identity upon save', async () => {
        // GIVEN
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(accountValues));
        vi.spyOn(accountService, 'save').mockReturnValue(of(new HttpResponse({ body: {} })));
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
        vi.spyOn(accountService, 'save').mockReturnValue(of(new HttpResponse({ body: {} })));

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
        vi.spyOn(accountService, 'save').mockReturnValue(of(new HttpResponse({ body: {} })));
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
        vi.spyOn(accountService, 'save').mockReturnValue(of(new HttpResponse({ body: {} })));
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
        vi.spyOn(accountService, 'save').mockReturnValue(of(new HttpResponse({ body: {} })));

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
        expect(accountService.save).toHaveBeenCalled();
        // The account values should have undefined for empty string values
        const savedUser = comp.currentUser();
        expect(savedUser?.firstName).toBeUndefined();
        expect(savedUser?.lastName).toBeUndefined();
        expect(savedUser?.email).toBeUndefined();
        expect(savedUser?.langKey).toBeUndefined();
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

    it('should update email when saving settings', async () => {
        // GIVEN
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(accountValues));
        vi.spyOn(accountService, 'save').mockReturnValue(of(new HttpResponse({ body: {} })));

        // WHEN
        comp.ngOnInit();
        await vi.waitFor(() => expect(comp.currentUser()).toBeDefined());

        // Update email in form
        comp.settingsForm.patchValue({
            email: 'new.email@mail.com',
        });
        comp.saveSettings();

        // THEN
        expect(accountService.save).toHaveBeenCalled();
        const savedUser = comp.currentUser();
        expect(savedUser?.email).toBe('new.email@mail.com');
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
            expect(compDisabled.isRegistrationEnabled).toBe(false);
        });
    });

    describe('when email pattern is configured', () => {
        let compWithPattern: SettingsComponent;

        beforeEach(() => {
            // Override ProfileService mock to return email pattern
            const profileService = TestBed.inject(ProfileService);
            vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({
                registrationEnabled: true,
                allowedEmailPattern: '^.*@university\\.edu$',
                allowedEmailPatternReadable: '@university.edu',
            } as any);

            const fixture = TestBed.createComponent(SettingsComponent);
            compWithPattern = fixture.componentInstance;
        });

        it('should use email pattern validation when configured', () => {
            expect(compWithPattern.allowedEmailPattern).toBe('^.*@university\\.edu$');
            expect(compWithPattern.allowedEmailPatternReadable).toBe('@university.edu');

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
            expect(compUndefined.isRegistrationEnabled).toBe(false);
        });
    });
});
