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
import { RepositoryAuthenticationMethod } from './code-button.component';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { CdkCopyToClipboard } from '@angular/cdk/clipboard';
import { DomSanitizer } from '@angular/platform-browser';
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
type LabeledAuthenticationMethodData = { authenticationMethodData: AuthenticationMethodData; label: string };
type IdeLinkData = { url: string; name: string };
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
    protected readonly labeledAuthenticationMethodData = computed(() => this.getLabeledAuthenticationMethodData());
    protected readonly selectedLabeledAuthenticationMethodData = linkedSignal(() => this.initializeSelectedLabeledAuthenticationMethodData());
    protected readonly alternativeAuthenticationMethodAvailable = computed(() => this.isAlternativeAuthenticationMethodAvailable());
    protected readonly repositoryDisplayUrl = computed(() => this.getRepositoryDisplayUrl());
    protected readonly repositoryCopyUrl = computed(() => this.selectedLabeledAuthenticationMethodData().authenticationMethodData.url);
    protected readonly ideLinkData = computed(() => this.getIdeLinkData());
    protected readonly sourceTreeUrl = computed(() => this.getSourceTreeUrl());

    protected selectAuthenticationMethod(labeledAuthenticationMethodData: LabeledAuthenticationMethodData) {
        this.selectedLabeledAuthenticationMethodData.set(labeledAuthenticationMethodData);
        this.localStorageService.store<RepositoryAuthenticationMethod>('code-button-state', labeledAuthenticationMethodData.authenticationMethodData.authenticationMethod);
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

    private initializeSelectedLabeledAuthenticationMethodData(): LabeledAuthenticationMethodData {
        const labeledAuthenticationMethodData = this.labeledAuthenticationMethodData();
        const initialAuthenticationMethod = this.localStorageService.retrieve<RepositoryAuthenticationMethod>('code-button-state');
        if (initialAuthenticationMethod === RepositoryAuthenticationMethod.Password) {
            const tokenAuthenticationMethodData = labeledAuthenticationMethodData.find((data) => {
                return data.authenticationMethodData.authenticationMethod === RepositoryAuthenticationMethod.Token;
            });
            if (tokenAuthenticationMethodData) {
                return tokenAuthenticationMethodData;
            }
        }
        return (
            labeledAuthenticationMethodData.find((data) => data.authenticationMethodData.authenticationMethod === initialAuthenticationMethod) ?? labeledAuthenticationMethodData[0]
        );
    }

    private getLabeledAuthenticationMethodData(): NonEmptyArray<LabeledAuthenticationMethodData> {
        const [firstAuthenticationMethodData, ...remainingAuthenticationMethodData] = this.authenticationMethodData();
        return [
            {
                authenticationMethodData: firstAuthenticationMethodData,
                label: this.getAuthenticationMechanismLabel(firstAuthenticationMethodData.authenticationMethod),
            },
            ...remainingAuthenticationMethodData.map((authenticationMethodData) => ({
                authenticationMethodData,
                label: this.getAuthenticationMechanismLabel(authenticationMethodData.authenticationMethod),
            })),
        ];
    }

    private isAlternativeAuthenticationMethodAvailable(): boolean {
        return this.authenticationMethodData().some((data) => {
            return data.authenticationMethod === RepositoryAuthenticationMethod.Token || data.authenticationMethod === RepositoryAuthenticationMethod.SSH;
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
        const authenticationMethodData = this.selectedLabeledAuthenticationMethodData().authenticationMethodData;
        if (authenticationMethodData.authenticationMethod !== RepositoryAuthenticationMethod.Token) {
            return authenticationMethodData.url;
        }
        return authenticationMethodData.url.replace(/(:\/\/[^:]+:)[^@]+(@)/, '$1**********$2');
    }

    private getIdeLinkData(): IdeLinkData | undefined {
        const preferredIde = this.preferredIde();

        const ideUrl = this.externalCloningService.buildIdeUrl(this.selectedLabeledAuthenticationMethodData().authenticationMethodData.url, preferredIde);
        const sanitizedUrl = this.sanitizeUrl(ideUrl);
        if (!sanitizedUrl) {
            return undefined;
        }

        return { url: sanitizedUrl, name: preferredIde.name };
    }

    private getSourceTreeUrl(): string | undefined {
        const versionControlUrl = this.profileService.getProfileInfo().versionControlUrl;
        const selectedAuthenticationMethodData = this.selectedLabeledAuthenticationMethodData().authenticationMethodData;
        const sourceTreeUrl = this.externalCloningService.buildSourceTreeUrl(versionControlUrl, selectedAuthenticationMethodData.url);
        return this.sanitizeUrl(sourceTreeUrl);
    }

    /**
     * Returns the URL if Angular's URL sanitizer accepts it, otherwise undefined so the link is not rendered.
     *
     * The sanitizer only rejects script-bearing schemes, so the application schemes of the IDE deep links such as
     * `vscode://` and `sourcetree://` pass. The `[href]` binding sanitizes the string again, which is why the result
     * is not marked as trusted.
     */
    private sanitizeUrl(url: string | undefined): string | undefined {
        if (!url) {
            return undefined;
        }
        const sanitizedUrl = this.sanitizer.sanitize(SecurityContext.URL, url);
        // Angular rewrites a rejected URL to an inert `unsafe:` form rather than dropping it.
        return !sanitizedUrl || sanitizedUrl.startsWith('unsafe:') ? undefined : sanitizedUrl;
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
