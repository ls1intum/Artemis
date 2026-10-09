import { Component, inject, resource } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleExclamation, faCircleNotch, faCode } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective } from '@tumaet/ui-angular';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { CodeButtonPopoverContentComponent, NonEmptyArray, SshKeyState } from './code-button-popover-content.component';
import { RepositoryAuthenticationMethod } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { SshUserSettingsService } from 'app/account/user/settings/ssh-settings/ssh-user-settings.service';
import dayjs from 'dayjs/esm';

interface CodeButtonData {
    authenticationMechanisms: NonEmptyArray<RepositoryAuthenticationMethod>;
    sshKeyState: SshKeyState;
}

@Component({
    selector: 'jhi-code-button-new',
    templateUrl: './code-button-new.component.html',
    imports: [TumAetUiButtonDirective, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective, FaIconComponent, ArtemisTranslatePipe, CodeButtonPopoverContentComponent],
})
export class CodeButtonNewComponent {
    private readonly profileService = inject(ProfileService);
    private readonly sshUserSettingsService = inject(SshUserSettingsService);

    protected readonly faCircleExclamation = faCircleExclamation;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faCode = faCode;
    protected readonly dataResource = resource({ loader: () => this.loadCodeButtonData() });

    private async loadCodeButtonData(): Promise<CodeButtonData> {
        const authenticationMechanisms = this.getAuthenticationMechanisms();
        const sshKeyState = await this.getSshKeyState();

        if (!authenticationMechanisms || !sshKeyState) {
            throw new Error('Incomplete code button data');
        }

        return {
            authenticationMechanisms,
            sshKeyState,
        };
    }

    private getAuthenticationMechanisms(): NonEmptyArray<RepositoryAuthenticationMethod> | undefined {
        const authenticationMechanismStrings = this.profileService.getProfileInfo().repositoryAuthenticationMechanisms ?? [];
        const authenticationMechanisms = authenticationMechanismStrings.filter((method): method is RepositoryAuthenticationMethod =>
            Object.values(RepositoryAuthenticationMethod).includes(method as RepositoryAuthenticationMethod),
        );

        return authenticationMechanisms.length > 0 ? [authenticationMechanisms[0], ...authenticationMechanisms.slice(1)] : undefined;
    }

    private async getSshKeyState(): Promise<SshKeyState | undefined> {
        try {
            const sshKeys = await this.sshUserSettingsService.getCachedSshKeys();
            if (!sshKeys) {
                return undefined;
            }

            const now = dayjs();
            return sshKeys.length === 0
                ? { status: 'missing' }
                : {
                      status: 'configured',
                      hasExpiredKeys: sshKeys.some((key) => !!key.expiryDate && dayjs(key.expiryDate).isBefore(now)),
                  };
        } catch {
            return undefined;
        }
    }
}
