import { Component, inject, resource } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleExclamation, faCircleNotch, faCode } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective } from '@tumaet/ui-angular';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AuthenticationMethodData, CodeButtonPopoverContentComponent, NonEmptyArray, SshKeyState } from './code-button-popover-content.component';
import { RepositoryAuthenticationMethod } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { SshUserSettingsService } from 'app/account/user/settings/ssh-settings/ssh-user-settings.service';
import dayjs from 'dayjs/esm';

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
    protected readonly dataResource = resource({ loader: () => this.loadAuthenticationMethodData() });

    private async loadAuthenticationMethodData(): Promise<NonEmptyArray<AuthenticationMethodData>> {
        const authenticationMethods = this.getAuthenticationMethods();
        const authenticationMethodData = await Promise.all(authenticationMethods.map((authenticationMethod) => this.getAuthenticationMethodData(authenticationMethod)));
        if (authenticationMethodData.length === 0) {
            throw new Error('Incomplete code button data');
        }
        return [authenticationMethodData[0], ...authenticationMethodData.slice(1)];
    }

    private getAuthenticationMethods(): RepositoryAuthenticationMethod[] {
        const authenticationMechanismStrings = this.profileService.getProfileInfo().repositoryAuthenticationMechanisms ?? [];
        return authenticationMechanismStrings.filter((method): method is RepositoryAuthenticationMethod =>
            Object.values(RepositoryAuthenticationMethod).includes(method as RepositoryAuthenticationMethod),
        );
    }

    private async getAuthenticationMethodData(authenticationMethod: RepositoryAuthenticationMethod): Promise<AuthenticationMethodData> {
        switch (authenticationMethod) {
            case RepositoryAuthenticationMethod.SSH:
                return this.getSshAuthenticationMethodData();
            case RepositoryAuthenticationMethod.Token:
                return this.getTokenAuthenticationMethodData();
            case RepositoryAuthenticationMethod.Password:
                return this.getPasswordAuthenticationMethodData();
        }
    }

    private async getSshAuthenticationMethodData(): Promise<AuthenticationMethodData> {
        const sshKeyState = await this.getSshKeyState();
        if (!sshKeyState) {
            throw new Error('Incomplete SSH authentication data');
        }
        return { authenticationMethod: RepositoryAuthenticationMethod.SSH, url: 'SSH', sshKeyState };
    }

    private getTokenAuthenticationMethodData(): AuthenticationMethodData {
        return {
            authenticationMethod: RepositoryAuthenticationMethod.Token,
            url: 'https://artemis-user:**********@artemis.example.com/very-long-course-name/programming-exercises/example-repository.git',
        };
    }

    private getPasswordAuthenticationMethodData(): AuthenticationMethodData {
        return {
            authenticationMethod: RepositoryAuthenticationMethod.Password,
            url: 'https://artemis-user@artemis.example.com/very-long-course-name/programming-exercises/example-repository.git',
        };
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
