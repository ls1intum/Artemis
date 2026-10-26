import { Component, WritableSignal, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MODULE_FEATURE_PASSKEY } from 'app/app.constants';
import { CredentialRevocationSettingsComponent } from 'app/account/user/settings/credential-revocation-settings/credential-revocation-settings.component';
import { PasskeySettingsComponent } from 'app/account/user/settings/passkey-settings/passkey-settings.component';
import { SshUserSettingsComponent } from 'app/account/user/settings/ssh-settings/ssh-user-settings.component';
import { VcsAccessTokenOverviewComponent } from 'app/account/user/settings/vcs-access-token-overview/vcs-access-token-overview.component';
import { VcsAccessTokensSettingsComponent } from 'app/account/user/settings/vcs-access-tokens-settings/vcs-access-tokens-settings.component';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { CredentialsSettingsV2Component } from './credentials-settings-v2.component';

@Component({ selector: 'jhi-passkey-settings', template: '' })
class MockPasskeySettingsComponent {}

@Component({ selector: 'jhi-ssh-user-settings', template: '' })
class MockSshUserSettingsComponent {}

@Component({ selector: 'jhi-vcs-access-tokens-settings', template: '' })
class MockVcsAccessTokensSettingsComponent {}

@Component({ selector: 'jhi-vcs-access-token-overview', template: '' })
class MockVcsAccessTokenOverviewComponent {}

@Component({ selector: 'jhi-credential-revocation-settings', template: '' })
class MockCredentialRevocationSettingsComponent {}

describe('CredentialsSettingsV2Component', () => {
    let fixture: ComponentFixture<CredentialsSettingsV2Component>;
    let accountService: { isAtLeastTutor: WritableSignal<boolean> };
    let profileService: { isModuleFeatureActive: ReturnType<typeof vi.fn> };

    beforeEach(async () => {
        accountService = { isAtLeastTutor: signal(false) };
        profileService = { isModuleFeatureActive: vi.fn().mockReturnValue(false) };

        await TestBed.configureTestingModule({
            imports: [CredentialsSettingsV2Component],
            providers: [
                { provide: AccountService, useValue: accountService },
                { provide: ProfileService, useValue: profileService },
            ],
        })
            .overrideComponent(CredentialsSettingsV2Component, {
                remove: {
                    imports: [
                        PasskeySettingsComponent,
                        SshUserSettingsComponent,
                        VcsAccessTokensSettingsComponent,
                        VcsAccessTokenOverviewComponent,
                        CredentialRevocationSettingsComponent,
                    ],
                },
                add: {
                    imports: [
                        MockPasskeySettingsComponent,
                        MockSshUserSettingsComponent,
                        MockVcsAccessTokensSettingsComponent,
                        MockVcsAccessTokenOverviewComponent,
                        MockCredentialRevocationSettingsComponent,
                    ],
                },
            })
            .compileComponents();
    });

    const createComponent = (): void => {
        fixture = TestBed.createComponent(CredentialsSettingsV2Component);
        fixture.detectChanges();
    };

    it('always renders SSH, Access Tokens, and credential revocation', () => {
        createComponent();

        expect(fixture.nativeElement.querySelector('jhi-ssh-user-settings')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('jhi-vcs-access-token-overview')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('jhi-credential-revocation-settings')).toBeTruthy();
    });

    it('does not instantiate Passkeys or VCS Token when unavailable', () => {
        createComponent();

        expect(fixture.nativeElement.querySelector('jhi-passkey-settings')).toBeFalsy();
        expect(fixture.nativeElement.querySelector('jhi-vcs-access-tokens-settings')).toBeFalsy();
    });

    it('renders Passkeys when the passkey module is available', () => {
        profileService.isModuleFeatureActive.mockImplementation((feature) => feature === MODULE_FEATURE_PASSKEY);

        createComponent();

        expect(fixture.nativeElement.querySelector('jhi-passkey-settings')).toBeTruthy();
    });

    it('renders VCS Token for tutors', () => {
        accountService.isAtLeastTutor.set(true);

        createComponent();

        expect(fixture.nativeElement.querySelector('jhi-vcs-access-tokens-settings')).toBeTruthy();
    });
});
