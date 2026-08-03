/**
 * Vitest tests for PasswordComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
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

describe('Password Component Tests', () => {
    describe('PasswordComponent', () => {
        let comp: PasswordComponent;
        let fixture: ComponentFixture<PasswordComponent>;
        let service: PasswordService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [PasswordComponent],
                providers: [
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

        it('should show error if passwords do not match', () => {
            // GIVEN
            comp.passwordForm.patchValue({
                newPassword: 'password1',
                confirmPassword: 'password2',
            });
            // WHEN
            comp.changePassword();
            // THEN
            expect(comp.doNotMatch()).toBe(true);
            expect(comp.error()).toBe(false);
            expect(comp.success()).toBe(false);
        });

        it('should call Auth.changePassword when passwords match', () => {
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
            comp.changePassword();

            // THEN
            // Nothing is revoked unless the user says the old password may have been compromised, so a routine rotation
            // never costs them their authenticators, keys or tokens.
            expect(service.changePassword).toHaveBeenCalledWith(passwordValues.newPassword, passwordValues.currentPassword, undefined);
        });

        it('should revoke the selected credentials when the old password may be compromised', () => {
            vi.spyOn(service, 'changePassword').mockReturnValue(of(void 0));
            comp.passwordForm.patchValue({ currentPassword: 'oldPassword', newPassword: 'myPassword', confirmPassword: 'myPassword' });

            comp.onPasswordMayBeCompromisedChange(true);
            comp.revokeSshKeys.set(false);
            comp.changePassword();

            expect(service.changePassword).toHaveBeenCalledWith('myPassword', 'oldPassword', { passkeys: true, sshKeys: false, vcsAccessTokens: true });
        });

        it('should revoke nothing again when the user unticks the compromise question', () => {
            vi.spyOn(service, 'changePassword').mockReturnValue(of(void 0));
            comp.passwordForm.patchValue({ currentPassword: 'oldPassword', newPassword: 'myPassword', confirmPassword: 'myPassword' });

            comp.onPasswordMayBeCompromisedChange(true);
            comp.onPasswordMayBeCompromisedChange(false);
            comp.changePassword();

            expect(service.changePassword).toHaveBeenCalledWith('myPassword', 'oldPassword', undefined);
        });

        it('should set success to true upon success', () => {
            // GIVEN
            vi.spyOn(service, 'changePassword').mockReturnValue(of(void 0));
            comp.passwordForm.patchValue({
                newPassword: 'myPassword',
                confirmPassword: 'myPassword',
            });

            // WHEN
            comp.changePassword();

            // THEN
            expect(comp.doNotMatch()).toBe(false);
            expect(comp.error()).toBe(false);
            expect(comp.success()).toBe(true);
        });

        it('should notify of error if change password fails', () => {
            // GIVEN
            vi.spyOn(service, 'changePassword').mockReturnValue(throwError(() => new Error('ERROR')));
            comp.passwordForm.patchValue({
                newPassword: 'myPassword',
                confirmPassword: 'myPassword',
            });

            // WHEN
            comp.changePassword();

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
});
