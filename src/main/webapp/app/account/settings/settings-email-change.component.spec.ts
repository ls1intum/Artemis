/**
 * Vitest tests for the email change part of the SettingsComponent template.
 */
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { SettingsComponent } from 'app/account/settings/settings.component';
import { User } from 'app/account/user/user.model';
import { PendingEmailChangeDTO } from 'app/account/user/shared/dto/pending-email-change.dto';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('SettingsComponent email change', () => {
    let fixture: ComponentFixture<SettingsComponent>;
    let accountService: AccountService;

    const user: User = {
        internal: true,
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
        currentAddressConfirmed: true,
        newAddressConfirmed: false,
    };

    function setUp(mailConfigured: boolean, pending?: PendingEmailChangeDTO) {
        TestBed.configureTestingModule({
            imports: [SettingsComponent],
            providers: [
                LocalStorageService,
                SessionStorageService,
                ProfileService,
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        const profileService = TestBed.inject(ProfileService);
        vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ registrationEnabled: true, mailConfigured } as any);
        vi.spyOn(profileService, 'isModuleFeatureActive').mockReturnValue(false);
        accountService = TestBed.inject(AccountService);
        vi.spyOn(accountService, 'identity').mockReturnValue(Promise.resolve(user));
        vi.spyOn(accountService, 'getPendingEmailChange').mockReturnValue(of(pending));
        fixture = TestBed.createComponent(SettingsComponent);
        fixture.detectChanges();
    }

    async function render() {
        await vi.waitFor(() => expect(fixture.componentInstance.currentUser()).toBeDefined());
        fixture.detectChanges();
        await fixture.whenStable();
    }

    function byTestId(testId: string) {
        return fixture.debugElement.query(By.css(`[data-testid="${testId}"]`));
    }

    beforeEach(() => {
        TestBed.resetTestingModule();
    });

    it('should show the pending email change with the confirmation state of both addresses', async () => {
        setUp(true, pendingEmailChange);
        await render();

        const notice = byTestId('pending-email-change');
        expect(notice).not.toBeNull();
        expect(byTestId('pending-email-change-new-email').nativeElement.textContent).toBe('new.email@mail.com');
        expect(byTestId('pending-email-change-current').nativeElement.textContent).toContain('john.doe@mail.com');
        expect(byTestId('pending-email-change-current').nativeElement.textContent).toContain('settings.pendingEmailChange.confirmed');
        expect(byTestId('pending-email-change-new').nativeElement.textContent).toContain('settings.pendingEmailChange.notConfirmed');
        expect(byTestId('pending-email-change-expires').nativeElement.textContent).not.toBe('');
    });

    it('should not show a notice when no email change is pending', async () => {
        setUp(true, undefined);
        await render();

        expect(byTestId('pending-email-change')).toBeNull();
    });

    it('should call the service when the resend and cancel buttons are clicked', async () => {
        setUp(true, pendingEmailChange);
        const resendSpy = vi.spyOn(accountService, 'resendPendingEmailChange').mockReturnValue(of(pendingEmailChange));
        const cancelSpy = vi.spyOn(accountService, 'cancelPendingEmailChange').mockReturnValue(of(undefined));
        await render();

        byTestId('resend-email-change-button').query(By.css('button')).nativeElement.click();
        expect(resendSpy).toHaveBeenCalledOnce();

        fixture.detectChanges();
        byTestId('cancel-email-change-button').query(By.css('button')).nativeElement.click();
        expect(cancelSpy).toHaveBeenCalledOnce();

        fixture.detectChanges();
        expect(byTestId('pending-email-change')).toBeNull();
    });

    it('should enable the email field and show no hint when mails are configured', async () => {
        setUp(true);
        await render();

        expect(byTestId('email-input').nativeElement.disabled).toBe(false);
        expect(byTestId('settings-mail-not-configured-hint')).toBeNull();
    });

    it('should disable the email field and show a hint when mails are not configured', async () => {
        setUp(false);
        await render();

        expect(byTestId('email-input').nativeElement.disabled).toBe(true);
        expect(byTestId('settings-mail-not-configured-hint')).not.toBeNull();
    });
});
