import { Component, SecurityContext, computed, inject, input, linkedSignal, model, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCaretDown, faCheck, faCodeBranch, faExternalLink, faLaptopCode, faQuestionCircle } from '@fortawesome/free-solid-svg-icons';
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
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { Ide } from 'app/account/user/settings/ide-preferences/ide.model';
import { ExternalCloningService } from 'app/programming/shared/services/external-cloning.service';
import { TheiaService } from 'app/programming/shared/services/theia.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';

export type NonEmptyArray<T> = readonly [T, ...T[]];
export type SshKeyState = { status: 'missing' } | { status: 'configured'; hasExpiredKeys: boolean };
export type AuthenticationMethodData =
    | { authenticationMethod: RepositoryAuthenticationMethod.SSH; url: string; sshKeyState: SshKeyState }
    | { authenticationMethod: RepositoryAuthenticationMethod.Token; url: string }
    | { authenticationMethod: RepositoryAuthenticationMethod.Password; url: string };
type IdeLinkData = { url: SafeUrl; name: string };
export type TheiaLinkData = { theiaPortalURL: string; theiaImage: string; repositoryUrl: string; userName: string; userEmail: string };

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
    private readonly localStorageService = inject(LocalStorageService);
    private readonly externalCloningService = inject(ExternalCloningService);
    private readonly sanitizer = inject(DomSanitizer);
    private readonly theiaService = inject(TheiaService);
    private readonly profileService = inject(ProfileService);

    protected readonly faCaretDown = faCaretDown;
    protected readonly faCheck = faCheck;
    protected readonly faCodeBranch = faCodeBranch;
    protected readonly faCopy = faCopy;
    protected readonly faExternalLink = faExternalLink;
    protected readonly faLaptopCode = faLaptopCode;
    protected readonly faQuestionCircle = faQuestionCircle;
    protected readonly RepositoryAuthenticationMethod = RepositoryAuthenticationMethod;
    protected readonly sshSettingsUrl = `${window.location.origin}/user-settings/ssh`;

    readonly repositoryViewLink = input.required<(string | number)[]>();
    readonly authenticationMethodData = input.required<NonEmptyArray<AuthenticationMethodData>>();
    readonly preferredIde = input.required<Ide>();
    readonly theiaLinkData = input<TheiaLinkData>();
    readonly showAlternatives = model.required<boolean>();
    protected readonly copiedRepositoryUrl = signal(false);
    protected readonly selectedAuthenticationMethodData = linkedSignal(() => this.initializeSelectedAuthenticationMethodData());
    protected readonly selectedAuthenticationMethodLabel = computed(() => this.getAuthenticationMechanismLabel(this.selectedAuthenticationMethodData().authenticationMethod));
    protected readonly authenticationMethodDataAndLabels = computed(() => this.getAuthenticationMechanismsWithLabels());
    protected readonly tokenAuthenticationAvailable = computed(() => this.isTokenAuthenticationAvailable());
    protected readonly repositoryDisplayUrl = computed(() => this.getRepositoryDisplayUrl());
    protected readonly repositoryCopyUrl = computed(() => this.selectedAuthenticationMethodData().url);
    protected readonly ideLinkData = computed(() => this.getIdeLinkData());
    protected readonly sourceTreeUrl = computed(() => this.getSourceTreeUrl());

    protected selectAuthenticationMethod(authenticationMethodData: AuthenticationMethodData) {
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

    protected startOnlineIDE(theiaLinkData: TheiaLinkData): Promise<void> {
        return this.theiaService.startOnlineIDE(
            theiaLinkData.theiaPortalURL,
            theiaLinkData.theiaImage,
            theiaLinkData.repositoryUrl,
            theiaLinkData.userName,
            theiaLinkData.userEmail,
        );
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

    private isTokenAuthenticationAvailable(): boolean {
        return this.authenticationMethodData().some((data) => {
            return data.authenticationMethod === RepositoryAuthenticationMethod.Token;
        });
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

    private getIdeLinkData(): IdeLinkData | undefined {
        const preferredIde = this.preferredIde();

        const ideUrl = this.externalCloningService.buildIdeUrl(this.selectedAuthenticationMethodData().url, preferredIde);
        if (!ideUrl) {
            return undefined;
        }
        const sanitizedUrl = this.sanitizer.sanitize(SecurityContext.URL, ideUrl);
        if (!sanitizedUrl || sanitizedUrl.startsWith('unsafe:')) {
            return undefined;
        }

        return {
            url: this.sanitizer.bypassSecurityTrustUrl(sanitizedUrl),
            name: preferredIde.name,
        };
    }

    private getSourceTreeUrl(): SafeUrl | undefined {
        const versionControlUrl = this.profileService.getProfileInfo().versionControlUrl;
        const selectedAuthenticationMethodData = this.selectedAuthenticationMethodData();
        const sourceTreeUrl = this.externalCloningService.buildSourceTreeUrl(versionControlUrl, selectedAuthenticationMethodData.url);
        if (!sourceTreeUrl) {
            return undefined;
        }
        const sanitizedUrl = this.sanitizer.sanitize(SecurityContext.URL, sourceTreeUrl);
        if (!sanitizedUrl || sanitizedUrl.startsWith('unsafe:')) {
            return undefined;
        }

        return this.sanitizer.bypassSecurityTrustUrl(sanitizedUrl);
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
