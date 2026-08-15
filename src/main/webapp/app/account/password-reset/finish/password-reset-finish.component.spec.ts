/**
 * Vitest tests for PasswordResetFinishComponent.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { CredentialRevocationConfirmationService } from 'app/account/shared/credential-revocation-confirmation.service';
import { ElementRef } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormBuilder } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { of, throwError } from 'rxjs';

import { PasswordResetFinishComponent } from 'app/account/password-reset/finish/password-reset-finish.component';
import { PasswordResetFinishService } from 'app/account/password-reset/finish/password-reset-finish.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { provideHttpClient } from '@angular/common/http';

describe('Component Tests', () => {
    describe('PasswordResetFinishComponent', () => {
        let fixture: ComponentFixture<PasswordResetFinishComponent>;
        let comp: PasswordResetFinishComponent;
        let passwordResetFinishService: PasswordResetFinishService;

        beforeEach(() => {
            fixture = TestBed.configureTestingModule({
                imports: [PasswordResetFinishComponent],
                providers: [
                    { provide: CredentialRevocationConfirmationService, useValue: { confirm: () => Promise.resolve(true) } },
                    FormBuilder,
                    {
                        provide: ActivatedRoute,
                        useValue: new MockActivatedRoute({ key: 'XYZPDQ' }),
                    },
                    LocalStorageService,
                    SessionStorageService,
                    { provide: ProfileService, useClass: MockProfileService },
                    provideHttpClient(),
                ],
            })
                .overrideTemplate(PasswordResetFinishComponent, '')
                .createComponent(PasswordResetFinishComponent);
        });

        beforeEach(() => {
            fixture = TestBed.createComponent(PasswordResetFinishComponent);
            comp = fixture.componentInstance;
            passwordResetFinishService = TestBed.inject(PasswordResetFinishService);
            comp.ngOnInit();
        });

        it('should ask before the reset deletes every credential, and abort when dismissed', async () => {
            // This page arrives with all three options selected, so a plain submit would delete every authenticator, key and

            // token. It is the easiest destructive action in the product to trigger by accident, so it must be confirmed.

            const confirmation = TestBed.inject(CredentialRevocationConfirmationService);

            const confirmSpy = vi.spyOn(confirmation, 'confirm').mockResolvedValue(false);

            const finishSpy = vi.spyOn(TestBed.inject(PasswordResetFinishService), 'completePasswordReset');

            comp.passwordForm.patchValue({ newPassword: 'new-Password-1', confirmPassword: 'new-Password-1' });

            await comp.finishReset();

            expect(confirmSpy).toHaveBeenCalledExactlyOnceWith({ passkeys: true, sshKeys: true, vcsAccessTokens: true });

            expect(finishSpy).not.toHaveBeenCalled();

            expect(comp.success()).toBe(false);
        });

        it('should not ask when the user deselected every credential type', async () => {
            // Nothing is being deleted, so the reset completes without a dialog.

            const confirmation = TestBed.inject(CredentialRevocationConfirmationService);

            const confirmSpy = vi.spyOn(confirmation, 'confirm');

            vi.spyOn(TestBed.inject(PasswordResetFinishService), 'completePasswordReset').mockReturnValue(of({}));

            comp.revokePasskeys.set(false);

            comp.revokeSshKeys.set(false);

            comp.revokeVcsAccessTokens.set(false);

            comp.passwordForm.patchValue({ newPassword: 'new-Password-1', confirmPassword: 'new-Password-1' });

            await comp.finishReset();

            expect(confirmSpy).toHaveBeenCalledExactlyOnceWith({ passkeys: false, sshKeys: false, vcsAccessTokens: false });

            expect(comp.success()).toBe(true);
        });

        it('should define its initial state', () => {
            expect(comp.initialized()).toBe(true);
            expect(comp.resetKey()).toBe('XYZPDQ');
        });

        it('sets focus after the view has been initialized', () => {
            const mockElement = document.createElement('input');
            const focusSpy = vi.spyOn(mockElement, 'focus');

            // Mock the viewChild signal to return the element
            vi.spyOn(comp, 'newPasswordInput').mockReturnValue({ nativeElement: mockElement } as ElementRef);

            comp.ngAfterViewInit();

            expect(focusSpy).toHaveBeenCalledOnce();
        });

        it('should ensure the two passwords entered match', async () => {
            comp.passwordForm.patchValue({
                newPassword: 'password',
                confirmPassword: 'non-matching',
            });

            await comp.finishReset();

            expect(comp.doNotMatch()).toBe(true);
        });

        it('should update success to true after resetting password', async () => {
            vi.spyOn(passwordResetFinishService, 'completePasswordReset').mockReturnValue(of({}));
            comp.passwordForm.patchValue({
                newPassword: 'password',
                confirmPassword: 'password',
            });

            await comp.finishReset();

            expect(passwordResetFinishService.completePasswordReset).toHaveBeenCalledWith('XYZPDQ', 'password', { passkeys: true, sshKeys: true, vcsAccessTokens: true });
            expect(comp.success()).toBe(true);
        });

        it('should default to revoking every credential type', () => {
            // The safe outcome needs no thought: completing a reset only proves control of the mailbox, so keeping a
            // credential has to be the deliberate act rather than the default.
            expect(comp.revokePasskeys()).toBe(true);
            expect(comp.revokeSshKeys()).toBe(true);
            expect(comp.revokeVcsAccessTokens()).toBe(true);
        });

        it('should send the credentials the user chose to keep', async () => {
            // Forgetting a password is not the same as losing it, so a user who kept their keys must not have them
            // deleted anyway.
            vi.spyOn(passwordResetFinishService, 'completePasswordReset').mockReturnValue(of({}));
            comp.passwordForm.patchValue({ newPassword: 'password', confirmPassword: 'password' });
            comp.revokeSshKeys.set(false);
            comp.revokeVcsAccessTokens.set(false);

            await comp.finishReset();

            expect(passwordResetFinishService.completePasswordReset).toHaveBeenCalledWith('XYZPDQ', 'password', {
                passkeys: true,
                sshKeys: false,
                vcsAccessTokens: false,
            });
            expect(comp.success()).toBe(true);
        });

        it('should notify of generic error', async () => {
            vi.spyOn(passwordResetFinishService, 'completePasswordReset').mockReturnValue(throwError(() => new Error('ERROR')));
            comp.passwordForm.patchValue({
                newPassword: 'password',
                confirmPassword: 'password',
            });

            await comp.finishReset();

            expect(passwordResetFinishService.completePasswordReset).toHaveBeenCalledWith('XYZPDQ', 'password', { passkeys: true, sshKeys: true, vcsAccessTokens: true });
            expect(comp.success()).toBe(false);
            expect(comp.error()).toBe(true);
        });
    });
});
