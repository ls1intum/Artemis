import { Component, inject } from '@angular/core';
import { MODULE_FEATURE_AGENT_ACCESS, MODULE_FEATURE_PASSKEY } from 'app/app.constants';
import { AgentAccessSettingsComponent } from 'app/account/user/settings/credentials/agent-access-settings/agent-access-settings.component';
import { CredentialRevocationSettingsComponent } from 'app/account/user/settings/credentials/credential-revocation-settings/credential-revocation-settings.component';
import { PasskeySettingsComponent } from 'app/account/user/settings/credentials/passkey-settings/passkey-settings.component';
import { SshUserSettingsComponent } from 'app/account/user/settings/credentials/ssh-settings/ssh-user-settings.component';
import { VcsAccessTokenOverviewComponent } from 'app/account/user/settings/credentials/vcs-access-token-overview/vcs-access-token-overview.component';
import { VcsAccessTokensSettingsComponent } from 'app/account/user/settings/credentials/vcs-access-tokens-settings/vcs-access-tokens-settings.component';
import { AccountService } from 'app/core/auth/account.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

@Component({
    selector: 'jhi-credentials-settings',
    templateUrl: './credentials-settings.component.html',
    imports: [
        PasskeySettingsComponent,
        SshUserSettingsComponent,
        VcsAccessTokensSettingsComponent,
        VcsAccessTokenOverviewComponent,
        AgentAccessSettingsComponent,
        CredentialRevocationSettingsComponent,
    ],
})
export class CredentialsSettingsComponent {
    private readonly accountService = inject(AccountService);
    private readonly profileService = inject(ProfileService);

    readonly isPasskeyAvailable = this.profileService.isModuleFeatureActive(MODULE_FEATURE_PASSKEY);
    readonly isVcsTokenAvailable = () => this.accountService.isAtLeastTutor();
    readonly isAgentAccessAvailable = () => this.profileService.isModuleFeatureActive(MODULE_FEATURE_AGENT_ACCESS) && this.accountService.mayCreateAgentTokens();
}
