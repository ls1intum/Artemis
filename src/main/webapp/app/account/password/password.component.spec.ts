/**
 * Vitest tests for PasswordComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CredentialRevocationConfirmationService } from 'app/account/shared/credential-revocation-confirmation.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { of, throwError } from 'rxjs';

import { PasswordComponent } from 'app/account/password/password.component';
import { PasswordService } from 'app/account/password/password.service';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('Password Component Tests', () => {
    describe('PasswordComponent', () => {
        let comp: PasswordComponent;
        let fixture: ComponentFixture<PasswordComponent>;
        let service: PasswordService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [PasswordComponent],
                providers: [
                    { provide: CredentialRevocationConfirmationService, useValue: { confirm: () => Promise.resolve(true) } },
                    LocalStorageService,
                    SessionStorageService,
                    { provide: AccountService, useClass: MockAccountService },
                    { provide: ProfileService, useClass: MockProfileService },
                    provideHttpClient(),
                    provideHttpClientTesting(),
                ],
            })
                .overrideTemplate(PasswordComponent, '')
                .compileComponents();
        });

        beforeEach(() => {
            fixture = TestBed.createComponent(PasswordComponent);
            comp = fixture.componentInstance;
            service = TestBed.inject(PasswordService);
        });

        it('should show error if passwords do not match', async () => {
            // GIVEN
            comp.passwordForm.patchValue({
                newPassword: 'password1',
                confirmPassword: 'password2',
            });
            // WHEN
            await comp.changePassword();
            // THEN
            expect(comp.doNotMatch()).toBe(true);
            expect(comp.error()).toBe(false);
            expect(comp.success()).toBe(false);
        });

        it('should not interrupt a routine password change with a confirmation', async () => {
            // A dialog on every password change is one users learn to click through, which would weaken it exactly where
            // it matters. Nothing is being deleted here, so nothing is asked.
            const confirmation = TestBed.inject(CredentialRevocationConfirmationService);
            const confirmSpy = vi.spyOn(confirmation, 'confirm');
            const changeSpy = vi.spyOn(TestBed.inject(PasswordService), 'changePassword').mockReturnValue(of(undefined));
            comp.passwordForm.patchValue({ currentPassword: 'old', newPassword: 'new-Password-1', confirmPassword: 'new-Password-1' });

            await comp.changePassword();

            expect(confirmSpy).toHaveBeenCalledExactlyOnceWith(undefined);
            expect(changeSpy).toHaveBeenCalledOnce();
        });

        it('should not change the password when the revocation confirmation is dismissed', async () => {
            // Dismissing the dialog has to abort the whole submit, not just skip the revocation: the user was asked
            // about deleting their credentials and said no, so silently changing the password anyway would surprise them.
            const confirmation = TestBed.inject(CredentialRevocationConfirmationService);
            vi.spyOn(confirmation, 'confirm').mockResolvedValue(false);
            const changeSpy = vi.spyOn(TestBed.inject(PasswordService), 'changePassword').mockReturnValue(of(undefined));
            comp.onPasswordMayBeCompromisedChange(true);
            comp.passwordForm.patchValue({ currentPassword: 'old', newPassword: 'new-Password-1', confirmPassword: 'new-Password-1' });

            await comp.changePassword();

            expect(changeSpy).not.toHaveBeenCalled();
            expect(comp.success()).toBe(false);
        });

        it('should ask before deleting credentials alongside a password change', async () => {
            const confirmation = TestBed.inject(CredentialRevocationConfirmationService);
            const confirmSpy = vi.spyOn(confirmation, 'confirm');
            vi.spyOn(TestBed.inject(PasswordService), 'changePassword').mockReturnValue(of(undefined));
            comp.onPasswordMayBeCompromisedChange(true);
            comp.revokeSshKeys.set(false);
            comp.passwordForm.patchValue({ currentPassword: 'old', newPassword: 'new-Password-1', confirmPassword: 'new-Password-1' });

            await comp.changePassword();

            expect(confirmSpy).toHaveBeenCalledExactlyOnceWith({ passkeys: true, sshKeys: false, vcsAccessTokens: true });
        });

        it('should call Auth.changePassword when passwords match', async () => {
            // GIVEN
            const passwordValues = {
                currentPassword: 'oldPassword',
                newPassword: 'myPassword',
            };

            vi.spyOn(service, 'changePassword').mockReturnValue(of(void 0));

            comp.passwordForm.patchValue({
                currentPassword: passwordValues.currentPassword,
                newPassword: passwordValues.newPassword,
                confirmPassword: passwordValues.newPassword,
            });

            // WHEN
            await comp.changePassword();

            // THEN
            // Nothing is revoked unless the user says the old password may have been compromised, so a routine rotation
            // never costs them their authenticators, keys or tokens.
            expect(service.changePassword).toHaveBeenCalledWith(passwordValues.newPassword, passwordValues.currentPassword, undefined);
        });

        it('should revoke the selected credentials when the old password may be compromised', async () => {
            vi.spyOn(service, 'changePassword').mockReturnValue(of(void 0));
            comp.passwordForm.patchValue({ currentPassword: 'oldPassword', newPassword: 'myPassword', confirmPassword: 'myPassword' });

            comp.onPasswordMayBeCompromisedChange(true);
            comp.revokeSshKeys.set(false);
            await comp.changePassword();

            expect(service.changePassword).toHaveBeenCalledWith('myPassword', 'oldPassword', { passkeys: true, sshKeys: false, vcsAccessTokens: true });
        });

        it('should revoke nothing again when the user unticks the compromise question', async () => {
            vi.spyOn(service, 'changePassword').mockReturnValue(of(void 0));
            comp.passwordForm.patchValue({ currentPassword: 'oldPassword', newPassword: 'myPassword', confirmPassword: 'myPassword' });

            comp.onPasswordMayBeCompromisedChange(true);
            comp.onPasswordMayBeCompromisedChange(false);
            await comp.changePassword();

            expect(service.changePassword).toHaveBeenCalledWith('myPassword', 'oldPassword', undefined);
        });

        it('should restore the safe defaults when the compromise choice is withdrawn', () => {
            // The three options are rendered inside `@if (passwordMayBeCompromised())`, so closing the section used to
            // leave a deselected group deselected. Reopening then presented options the user had been told default to
            // selected, while silently keeping a credential they would expect to be revoked.
            comp.onPasswordMayBeCompromisedChange(true);
            comp.revokeSshKeys.set(false);
            comp.revokeVcsAccessTokens.set(false);

            comp.onPasswordMayBeCompromisedChange(false);
            comp.onPasswordMayBeCompromisedChange(true);

            expect(comp.revokePasskeys()).toBe(true);
            expect(comp.revokeSshKeys()).toBe(true);
            expect(comp.revokeVcsAccessTokens()).toBe(true);
        });

        it('should set success to true upon success', async () => {
            // GIVEN
            vi.spyOn(service, 'changePassword').mockReturnValue(of(void 0));
            comp.passwordForm.patchValue({
                newPassword: 'myPassword',
                confirmPassword: 'myPassword',
            });

            // WHEN
            await comp.changePassword();

            // THEN
            expect(comp.doNotMatch()).toBe(false);
            expect(comp.error()).toBe(false);
            expect(comp.success()).toBe(true);
        });

        it('should notify of error if change password fails', async () => {
            // GIVEN
            vi.spyOn(service, 'changePassword').mockReturnValue(throwError(() => new Error('ERROR')));
            comp.passwordForm.patchValue({
                newPassword: 'myPassword',
                confirmPassword: 'myPassword',
            });

            // WHEN
            await comp.changePassword();

            // THEN
            expect(comp.doNotMatch()).toBe(false);
            expect(comp.success()).toBe(false);
            expect(comp.error()).toBe(true);
        });

        it('sets user on init', async () => {
            comp.ngOnInit();
            await vi.waitFor(() => expect(comp.user()).toBeDefined());
            const expectedUser = { id: 99, login: 'admin' } as User;
            expect(comp.user()).toEqual(expectedUser);
        });
    });

    /**
     * Renders the real template, which the suite above replaces with an empty one. The revocation checkboxes sit inside
     * the reactive passwordForm, so binding them without {@code standalone: true} would throw at render time rather than
     * at compile time, and no test that only drives the component class would notice.
     */
    describe('PasswordComponent revocation options', () => {
        let comp: PasswordComponent;
        let fixture: ComponentFixture<PasswordComponent>;

        beforeEach(async () => {
            await TestBed.configureTestingModule({
                imports: [PasswordComponent],
                providers: [
                    { provide: CredentialRevocationConfirmationService, useValue: { confirm: () => Promise.resolve(true) } },
                    LocalStorageService,
                    SessionStorageService,
                    { provide: AccountService, useClass: MockAccountService },
                    { provide: ProfileService, useClass: MockProfileService },
                    { provide: TranslateService, useClass: MockTranslateService },
                    provideHttpClient(),
                    provideHttpClientTesting(),
                ],
            }).compileComponents();

            fixture = TestBed.createComponent(PasswordComponent);
            comp = fixture.componentInstance;
            comp.passwordResetEnabled.set(true);
            comp.user.set({ id: 99, login: 'admin' } as User);
        });

        it('should render the options only once the user says the password may be compromised', () => {
            fixture.detectChanges();

            expect(fixture.nativeElement.querySelector('[data-testid="password-may-be-compromised"]')).not.toBeNull();
            expect(fixture.nativeElement.querySelector('[data-testid="password-revocation-options"]')).toBeNull();

            comp.onPasswordMayBeCompromisedChange(true);
            fixture.detectChanges();

            const options = fixture.nativeElement.querySelector('[data-testid="password-revocation-options"]');
            expect(options).not.toBeNull();
            expect(options.querySelectorAll('p-checkbox')).toHaveLength(3);
        });
    });
});
