import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MODULE_FEATURE_AGENT_ACCESS, MODULE_FEATURE_PASSKEY } from 'app/app.constants';
import { AgentAccessSettingsComponent } from 'app/account/user/settings/credentials/agent-access-settings/agent-access-settings.component';
import { CredentialRevocationSettingsComponent } from 'app/account/user/settings/credentials/credential-revocation-settings/credential-revocation-settings.component';
import { PasskeySettingsComponent } from 'app/account/user/settings/credentials/passkey-settings/passkey-settings.component';
import { SshUserSettingsComponent } from 'app/account/user/settings/credentials/ssh-settings/ssh-user-settings.component';
import { VcsAccessTokenOverviewComponent } from 'app/account/user/settings/credentials/vcs-access-token-overview/vcs-access-token-overview.component';
import { VcsAccessTokensSettingsComponent } from 'app/account/user/settings/credentials/vcs-access-tokens-settings/vcs-access-tokens-settings.component';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { CredentialsSettingsComponent } from './credentials-settings.component';

@Component({ selector: 'jhi-passkey-settings', template: '' })
class MockPasskeySettingsComponent {}

@Component({ selector: 'jhi-ssh-user-settings', template: '' })
class MockSshUserSettingsComponent {}

@Component({ selector: 'jhi-vcs-access-tokens-settings', template: '' })
class MockVcsAccessTokensSettingsComponent {}

@Component({ selector: 'jhi-vcs-access-token-overview', template: '' })
class MockVcsAccessTokenOverviewComponent {}

@Component({ selector: 'jhi-agent-access-settings', template: '' })
class MockAgentAccessSettingsComponent {}

@Component({ selector: 'jhi-credential-revocation-settings', template: '' })
class MockCredentialRevocationSettingsComponent {}

describe('CredentialsSettingsComponent', () => {
    let fixture: ComponentFixture<CredentialsSettingsComponent>;
    let accountService: { isAtLeastTutor: ReturnType<typeof vi.fn>; mayCreateAgentTokens: ReturnType<typeof vi.fn> };
    let profileService: { isModuleFeatureActive: ReturnType<typeof vi.fn> };

    beforeEach(async () => {
        accountService = { isAtLeastTutor: vi.fn().mockReturnValue(false), mayCreateAgentTokens: vi.fn().mockReturnValue(false) };
        profileService = { isModuleFeatureActive: vi.fn().mockReturnValue(false) };

        await TestBed.configureTestingModule({
            imports: [CredentialsSettingsComponent],
            providers: [
                { provide: AccountService, useValue: accountService },
                { provide: ProfileService, useValue: profileService },
            ],
        })
            .overrideComponent(CredentialsSettingsComponent, {
                remove: {
                    imports: [
                        PasskeySettingsComponent,
                        SshUserSettingsComponent,
                        VcsAccessTokensSettingsComponent,
                        VcsAccessTokenOverviewComponent,
                        AgentAccessSettingsComponent,
                        CredentialRevocationSettingsComponent,
                    ],
                },
                add: {
                    imports: [
                        MockPasskeySettingsComponent,
                        MockSshUserSettingsComponent,
                        MockVcsAccessTokensSettingsComponent,
                        MockVcsAccessTokenOverviewComponent,
                        MockAgentAccessSettingsComponent,
                        MockCredentialRevocationSettingsComponent,
                    ],
                },
            })
            .compileComponents();
    });

    const createComponent = (): void => {
        fixture = TestBed.createComponent(CredentialsSettingsComponent);
        fixture.detectChanges();
    };

    it('always renders SSH, Access Tokens, and credential revocation', () => {
        createComponent();

        expect(fixture.nativeElement.querySelector('jhi-ssh-user-settings')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('jhi-vcs-access-token-overview')).toBeTruthy();
        expect(fixture.nativeElement.querySelector('jhi-credential-revocation-settings')).toBeTruthy();
    });

    it('does not instantiate Passkeys, VCS Token, or Agent Access when unavailable', () => {
        createComponent();

        expect(fixture.nativeElement.querySelector('jhi-passkey-settings')).toBeFalsy();
        expect(fixture.nativeElement.querySelector('jhi-vcs-access-tokens-settings')).toBeFalsy();
        expect(fixture.nativeElement.querySelector('jhi-agent-access-settings')).toBeFalsy();
    });

    it('renders Passkeys when the passkey module is available', () => {
        profileService.isModuleFeatureActive.mockImplementation((feature) => feature === MODULE_FEATURE_PASSKEY);

        createComponent();

        expect(fixture.nativeElement.querySelector('jhi-passkey-settings')).toBeTruthy();
    });

    it('renders VCS Token for tutors', () => {
        accountService.isAtLeastTutor.mockReturnValue(true);

        createComponent();

        expect(fixture.nativeElement.querySelector('jhi-vcs-access-tokens-settings')).toBeTruthy();
    });

    it('renders Agent Access when the module is enabled and the user may create agent tokens', () => {
        profileService.isModuleFeatureActive.mockImplementation((feature) => feature === MODULE_FEATURE_AGENT_ACCESS);
        accountService.mayCreateAgentTokens.mockReturnValue(true);

        createComponent();

        expect(fixture.nativeElement.querySelector('jhi-agent-access-settings')).toBeTruthy();
    });

    it('refreshes only the credential lists affected by bulk revocation', () => {
        createComponent();
        const updateRegisteredPasskeys = vi.fn();
        const refreshSshKeys = vi.fn();
        const clearRevokedToken = vi.fn();
        const loadTokens = vi.fn();
        Object.defineProperties(fixture.componentInstance, {
            passkeySettings: { value: () => ({ updateRegisteredPasskeys }) },
            sshSettings: { value: () => ({ refreshSshKeys }) },
            vcsAccessTokenSettings: { value: () => ({ clearRevokedToken }) },
            vcsAccessTokenOverview: { value: () => ({ loadTokens }) },
        });

        fixture.componentInstance.onCredentialsRevoked({ passkeys: true, sshKeys: false, vcsAccessTokens: true });

        expect(updateRegisteredPasskeys).toHaveBeenCalledOnce();
        expect(refreshSshKeys).not.toHaveBeenCalled();
        expect(clearRevokedToken).toHaveBeenCalledOnce();
        expect(loadTokens).toHaveBeenCalledOnce();
    });
});
