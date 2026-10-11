import { Component, inject, input, resource } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleExclamation, faCircleNotch, faCode } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective } from '@tumaet/ui-angular';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AuthenticationMethodData, CodeButtonPopoverContentComponent, NonEmptyArray, SshKeyState } from './code-button-popover-content.component';
import { RepositoryAuthenticationMethod } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { SshUserSettingsService } from 'app/account/user/settings/ssh-settings/ssh-user-settings.service';
import dayjs from 'dayjs/esm';
import { AccountService } from 'app/core/auth/account.service';

@Component({
    selector: 'jhi-code-button-new',
    templateUrl: './code-button-new.component.html',
    imports: [TumAetUiButtonDirective, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective, FaIconComponent, ArtemisTranslatePipe, CodeButtonPopoverContentComponent],
})
export class CodeButtonNewComponent {
    private readonly accountService = inject(AccountService);
    private readonly profileService = inject(ProfileService);
    private readonly sshUserSettingsService = inject(SshUserSettingsService);

    protected readonly faCircleExclamation = faCircleExclamation;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faCode = faCode;

    readonly repositoryUri = input.required<string>();
    protected readonly dataResource = resource({
        params: () => this.repositoryUri(),
        loader: () => this.loadAuthenticationMethodData(),
    });

    private async loadAuthenticationMethodData(): Promise<NonEmptyArray<AuthenticationMethodData>> {
        const repositoryUri = this.repositoryUri();
        const authenticationMethods = this.getAuthenticationMethods();
        const login = await this.getLoginIfRequired(authenticationMethods);

        const authenticationMethodData = await Promise.all(
            authenticationMethods.map((authenticationMethod) => this.getAuthenticationMethodData(authenticationMethod, repositoryUri, login)),
        );
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

    private async getLoginIfRequired(authenticationMethods: RepositoryAuthenticationMethod[]): Promise<string | undefined> {
        const requiresLogin = authenticationMethods.some(
            (authenticationMethod) => authenticationMethod === RepositoryAuthenticationMethod.Token || authenticationMethod === RepositoryAuthenticationMethod.Password,
        );
        if (!requiresLogin) {
            return undefined;
        }

        const user = await this.accountService.identity();
        const login = user?.login;
        if (!login) {
            throw new Error('Incomplete token or password authentication data');
        }
        return login;
    }

    private async getAuthenticationMethodData(
        authenticationMethod: RepositoryAuthenticationMethod,
        repositoryUri: string,
        login: string | undefined,
    ): Promise<AuthenticationMethodData> {
        switch (authenticationMethod) {
            case RepositoryAuthenticationMethod.SSH:
                return this.getSshAuthenticationMethodData(repositoryUri);
            case RepositoryAuthenticationMethod.Token:
                return this.getTokenAuthenticationMethodData(repositoryUri, this.requireLogin(login));
            case RepositoryAuthenticationMethod.Password:
                return this.getPasswordAuthenticationMethodData(repositoryUri, this.requireLogin(login));
        }
    }

    private requireLogin(login: string | undefined): string {
        if (!login) {
            throw new Error('Incomplete token or password authentication data');
        }
        return login;
    }

    private async getSshAuthenticationMethodData(repositoryUri: string): Promise<AuthenticationMethodData> {
        const sshCloneUrlTemplate = this.profileService.getProfileInfo().sshCloneURLTemplate;
        const sshRepositoryUrl = this.getSshRepositoryUrl(repositoryUri, sshCloneUrlTemplate);

        const sshKeyState = await this.getSshKeyState();
        if (!sshCloneUrlTemplate || !sshKeyState) {
            throw new Error('Incomplete SSH authentication data');
        }

        return {
            authenticationMethod: RepositoryAuthenticationMethod.SSH,
            url: sshRepositoryUrl,
            sshKeyState,
        };
    }

    /**
     * Converts an HTTP(S) repository URI into the SSH clone URL configured by the server.
     *
     * For example, let `repositoryUri` be `https://artemis.example.com/scm/course/repository.git` and `sshCloneUrlTemplate`
     * be `ssh://git@artemis.example.com:7999/`. In this example, the regular expression matches `https://artemis.example.com/`,
     * optionally matches `scm/`, and captures `course/repository.git` as `$2`. The replacement appends
     * `$2` to `sshCloneUrlTemplate`, producing `ssh://git@artemis.example.com:7999/course/repository.git`.
     */
    private getSshRepositoryUrl(repositoryUri: string, sshCloneUrlTemplate: string): string {
        return repositoryUri.replace(/^\w*:\/\/[^/]*?\/(scm\/)?(.*)$/, sshCloneUrlTemplate + '$2');
    }

    private getTokenAuthenticationMethodData(repositoryUri: string, login: string): AuthenticationMethodData {
        // TODO: derive token and insert here, move * replacement into content component
        const credentials = `://${login}:**********@`;
        const url = this.insertCredentialsIntoUri(repositoryUri, credentials);

        return {
            authenticationMethod: RepositoryAuthenticationMethod.Token,
            url,
        };
    }

    private getPasswordAuthenticationMethodData(repositoryUri: string, login: string): AuthenticationMethodData {
        const credentials = `://${login}@`;
        const url = this.insertCredentialsIntoUri(repositoryUri, credentials);

        return {
            authenticationMethod: RepositoryAuthenticationMethod.Password,
            url,
        };
    }

    /**
     * Inserts new credentials into an HTTP(S) repository URI.
     *
     * If the URL contains an `@`, it has the format `https://username@vcs-server.com/repository.git`. The regular expression matches
     * the existing `://username@` part and replaces it with the new credentials.
     *
     * Otherwise, the URL has the format `https://vcs-server.com/repository.git`. In that case the regex matches `://`
     * and replaces it with the new credentials.
     */
    private insertCredentialsIntoUri(url: string, credentials: string): string {
        if (url.includes('@')) {
            return url.replace(/:\/\/.*@/, credentials);
        } else {
            return url.replace('://', credentials);
        }
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
