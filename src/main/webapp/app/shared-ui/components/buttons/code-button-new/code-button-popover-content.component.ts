import { Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCaretDown, faCheck, faExternalLink } from '@fortawesome/free-solid-svg-icons';
import { faCopy } from '@fortawesome/free-regular-svg-icons';
import {
    TumAetUiButtonDirective,
    TumAetUiMenuComponent,
    TumAetUiMenuItemDirective,
    TumAetUiMenuTriggerDirective,
    TumAetUiMessageComponent,
    TumAetUiTooltipDirective,
} from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { RepositoryAuthenticationMethod } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { CdkCopyToClipboard } from '@angular/cdk/clipboard';

export type NonEmptyArray<T> = readonly [T, ...T[]];
export type SshKeyState = { status: 'missing' } | { status: 'configured'; hasExpiredKeys: boolean };
export type AuthenticationMethodData =
    | { authenticationMethod: RepositoryAuthenticationMethod.SSH; url: string; sshKeyState: SshKeyState }
    | { authenticationMethod: RepositoryAuthenticationMethod.Token; url: string }
    | { authenticationMethod: RepositoryAuthenticationMethod.Password; url: string };

@Component({
    selector: 'jhi-code-button-popover-content',
    templateUrl: './code-button-popover-content.component.html',
    imports: [
        TranslateDirective,
        FaIconComponent,
        TumAetUiButtonDirective,
        TumAetUiMenuComponent,
        TumAetUiMenuItemDirective,
        TumAetUiMenuTriggerDirective,
        TumAetUiMessageComponent,
        TumAetUiTooltipDirective,
        CdkCopyToClipboard,
        RouterLink,
        ArtemisTranslatePipe,
    ],
})
export class CodeButtonPopoverContentComponent {
    private localStorageService = inject(LocalStorageService);

    protected readonly faCaretDown = faCaretDown;
    protected readonly faCheck = faCheck;
    protected readonly faCopy = faCopy;
    protected readonly faExternalLink = faExternalLink;
    protected readonly RepositoryAuthenticationMethod = RepositoryAuthenticationMethod;
    protected readonly sshSettingsUrl = `${window.location.origin}/user-settings/ssh`;

    readonly authenticationMethodData = input.required<NonEmptyArray<AuthenticationMethodData>>();
    readonly repositoryViewLink = input.required<(string | number)[]>();
    protected readonly selectedAuthenticationMethodData = linkedSignal(() => this.initializeSelectedAuthenticationMethodData());
    protected readonly selectedAuthenticationMethodLabel = computed(() => this.getAuthenticationMechanismLabel(this.selectedAuthenticationMethodData().authenticationMethod));
    protected readonly authenticationMethodDataAndLabels = computed(() => this.getAuthenticationMechanismsWithLabels());
    protected readonly repositoryDisplayUrl = computed(() => this.getRepositoryDisplayUrl());
    protected readonly repositoryCopyUrl = computed(() => this.selectedAuthenticationMethodData().url);
    protected readonly copiedRepositoryUrl = signal(false);

    selectAuthenticationMethod(authenticationMethodData: AuthenticationMethodData) {
        this.selectedAuthenticationMethodData.set(authenticationMethodData);
        this.localStorageService.store<RepositoryAuthenticationMethod>('code-button-state', authenticationMethodData.authenticationMethod);
    }

    protected onRepositoryUrlCopied(successful: boolean): void {
        if (!successful) {
            return;
        }
        this.copiedRepositoryUrl.set(true);
        setTimeout(() => this.copiedRepositoryUrl.set(false), 2000);
    }

    private initializeSelectedAuthenticationMethodData(): AuthenticationMethodData {
        const authenticationMethodData = this.authenticationMethodData();
        let initialAuthenticationMethod = this.localStorageService.retrieve<RepositoryAuthenticationMethod>('code-button-state');
        if (initialAuthenticationMethod === RepositoryAuthenticationMethod.Password) {
            initialAuthenticationMethod = RepositoryAuthenticationMethod.Token;
        }
        return authenticationMethodData.find((data) => data.authenticationMethod === initialAuthenticationMethod) ?? authenticationMethodData[0];
    }

    private getAuthenticationMechanismsWithLabels() {
        return this.authenticationMethodData().map((authenticationMethodData) => ({
            authenticationMethodData,
            label: this.getAuthenticationMechanismLabel(authenticationMethodData.authenticationMethod),
        }));
    }

    /**
     * Returns the repository URL for display without exposing a VCS access token.
     *
     * A token URL has the format `https://username:token@vcs-server.com/repository.git`. The regular expression captures
     * `://username:` as `$1` and `@` as `$2`, then replaces the token between them with `**********`.
     * URLs for other authentication methods are returned unchanged.
     */
    private getRepositoryDisplayUrl(): string {
        const authenticationMethodData = this.selectedAuthenticationMethodData();
        if (authenticationMethodData.authenticationMethod !== RepositoryAuthenticationMethod.Token) {
            return authenticationMethodData.url;
        }
        return authenticationMethodData.url.replace(/(:\/\/[^:]+:)[^@]+(@)/, '$1**********$2');
    }

    private getAuthenticationMechanismLabel(authenticationMechanism: RepositoryAuthenticationMethod): string {
        switch (authenticationMechanism) {
            case RepositoryAuthenticationMethod.Password:
                return 'HTTPS';
            case RepositoryAuthenticationMethod.Token:
                return 'Token';
            case RepositoryAuthenticationMethod.SSH:
                return 'SSH';
        }
    }
}
