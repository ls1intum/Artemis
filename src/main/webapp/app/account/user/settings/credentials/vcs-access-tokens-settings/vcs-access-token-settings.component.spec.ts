import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AccountService } from 'app/core/auth/account.service';
import { of, throwError } from 'rxjs';
import { User } from 'app/account/user/user.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { provideHttpClient } from '@angular/common/http';
import { VcsAccessTokensSettingsComponent } from 'app/account/user/settings/credentials/vcs-access-tokens-settings/vcs-access-tokens-settings.component';

describe('VcsAccessTokensSettingsComponent', () => {
    let fixture: ComponentFixture<VcsAccessTokensSettingsComponent>;
    let comp: VcsAccessTokensSettingsComponent;

    let accountServiceMock: {
        getAuthenticationState: ReturnType<typeof vi.fn>;
        deleteUserVcsAccessToken: ReturnType<typeof vi.fn>;
    };
    const alertServiceMock = { error: vi.fn(), addAlert: vi.fn(), success: vi.fn() };

    const expiryDate = '2030-01-01T12:00:00Z';

    beforeEach(async () => {
        vi.clearAllMocks();
        accountServiceMock = {
            getAuthenticationState: vi.fn(),
            deleteUserVcsAccessToken: vi.fn(),
        };

        await TestBed.configureTestingModule({
            providers: [
                { provide: AccountService, useValue: accountServiceMock },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AlertService, useValue: alertServiceMock },
                provideHttpClient(),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(VcsAccessTokensSettingsComponent);
        comp = fixture.componentInstance;
        TestBed.inject(TranslateService).use('en');

        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1, vcsAccessTokenExpiryDate: expiryDate } as User));
        accountServiceMock.deleteUserVcsAccessToken.mockReturnValue(of(undefined));
    });

    afterEach(() => {
        vi.restoreAllMocks();
        fixture.destroy();
        TestBed.resetTestingModule();
    });

    function render() {
        fixture.detectChanges();
        return fixture.nativeElement as HTMLElement;
    }

    it('should show the deprecation notice, the masked token and the delete button when the user still has a personal token', () => {
        const element = render();

        expect(comp.hasPersonalToken()).toBe(true);
        expect(element.querySelector('[data-testid="vcs-token-deprecation-notice"]')).not.toBeNull();
        expect(element.querySelector('table')).not.toBeNull();
        expect(element.textContent).toContain('***************');
        expect(element.querySelector('#delete-vcs-token-button')).not.toBeNull();
    });

    it('should show an expired personal token so that it can still be revoked', () => {
        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1, vcsAccessTokenExpiryDate: '2001-01-01T12:00:00Z' } as User));
        const element = render();

        expect(comp.hasPersonalToken()).toBe(true);
        expect(element.querySelector('#delete-vcs-token-button')).not.toBeNull();
    });

    it('should only show the deprecation notice when the user has no personal token', () => {
        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1 } as User));
        const element = render();

        expect(comp.hasPersonalToken()).toBe(false);
        expect(element.querySelector('[data-testid="vcs-token-deprecation-notice"]')).not.toBeNull();
        expect(element.querySelector('table')).toBeNull();
        expect(element.querySelector('#delete-vcs-token-button')).toBeNull();
        expect(element.innerHTML).toContain('artemisApp.userSettings.vcsAccessTokensSettingsPage.noTokenSet');
    });

    it('should not offer any way to create a personal token', () => {
        accountServiceMock.getAuthenticationState.mockReturnValue(of({ id: 1 } as User));
        const element = render();

        expect(element.querySelector('#add-new-token-button')).toBeNull();
        expect(element.querySelector('#create-vcs-token-button')).toBeNull();
        expect(element.querySelector('tumaet-ui-date-picker')).toBeNull();
    });

    it('should revoke the personal token and hide it afterwards', () => {
        const element = render();

        comp.deleteVcsAccessToken();
        fixture.detectChanges();

        expect(accountServiceMock.deleteUserVcsAccessToken).toHaveBeenCalledOnce();
        expect(alertServiceMock.success).toHaveBeenCalledWith('artemisApp.userSettings.vcsAccessTokensSettingsPage.deleteSuccess');
        expect(comp.currentUser()!.vcsAccessTokenExpiryDate).toBeUndefined();
        expect(comp.hasPersonalToken()).toBe(false);
        expect(element.querySelector('#delete-vcs-token-button')).toBeNull();
    });

    it('should keep the personal token and show an error when revoking fails', () => {
        accountServiceMock.deleteUserVcsAccessToken.mockReturnValue(throwError(() => new Error('Internal Server error')));
        render();

        comp.deleteVcsAccessToken();

        expect(accountServiceMock.deleteUserVcsAccessToken).toHaveBeenCalledOnce();
        expect(alertServiceMock.error).toHaveBeenCalledWith('artemisApp.userSettings.vcsAccessTokensSettingsPage.deleteFailure');
        expect(comp.hasPersonalToken()).toBe(true);
    });

    it('should hide the token when the credentials page reports it as revoked', () => {
        render();

        comp.clearRevokedToken();

        expect(comp.hasPersonalToken()).toBe(false);
    });
});
