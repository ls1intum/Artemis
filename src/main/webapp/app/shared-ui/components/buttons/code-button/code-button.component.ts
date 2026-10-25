import { Component, type ResourceParamsContext, type ResourceSnapshot, computed, inject, input, resource, resourceFromSnapshots, signal } from '@angular/core';
import { BreakpointObserver } from '@angular/cdk/layout';
import { toSignal } from '@angular/core/rxjs-interop';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleExclamation, faCircleNotch, faCode } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective } from '@tumaet/ui-angular';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AuthenticationMethodData, CodeButtonPopoverContentComponent, NonEmptyArray, SshKeyState, TheiaLinkData } from './code-button-popover-content.component';
import { SshUserSettingsService } from 'app/account/user/settings/ssh-settings/ssh-user-settings.service';
import dayjs from 'dayjs/esm';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { Observable, firstValueFrom } from 'rxjs';
import { IdeSettingsService } from 'app/account/user/settings/ide-preferences/ide-settings.service';
import { Ide } from 'app/account/user/settings/ide-preferences/ide.model';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { User } from 'app/account/user/user.model';
import { MODULE_FEATURE_THEIA } from 'app/app.constants';
import { map } from 'rxjs/operators';
import { notUndefined } from 'app/foundation/util/string-pure.utils';

export enum RepositoryAuthenticationMethod {
    Password = 'password',
    Token = 'token',
    SSH = 'ssh',
}

export type CodeButtonSize = 'small' | 'default';

export type VcsAccessTokenSourceInfo =
    | { returnedTokenType: 'participation-token'; participationId: number }
    | { returnedTokenType: 'repository-token'; repositoryType: RepositoryType.USER; participationId: number }
    | { returnedTokenType: 'repository-token'; repositoryType: RepositoryType.TEMPLATE | RepositoryType.SOLUTION | RepositoryType.TESTS }
    | { returnedTokenType: 'repository-token'; repositoryType: RepositoryType.AUXILIARY; auxiliaryRepositoryId: number };

const BREAKPOINTS = {
    md: '(min-width: 768px)',
    xl: '(min-width: 1200px)',
} as const;

type CodeButtonLoadingState =
    | { status: 'idle' | 'loading' | 'error' }
    | { status: 'complete'; authenticationMethodData: NonEmptyArray<AuthenticationMethodData>; preferredIde: Ide; theiaLinkData: TheiaLinkData | undefined };

@Component({
    selector: 'jhi-code-button',
    templateUrl: './code-button.component.html',
    imports: [TumAetUiButtonDirective, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective, FaIconComponent, ArtemisTranslatePipe, CodeButtonPopoverContentComponent],
})
export class CodeButtonComponent {
    private readonly accountService = inject(AccountService);
    private readonly profileService = inject(ProfileService);
    private readonly sshUserSettingsService = inject(SshUserSettingsService);
    private readonly programmingExerciseService = inject(ProgrammingExerciseService);
    private readonly ideSettingsService = inject(IdeSettingsService);
    private readonly breakpointObserver = inject(BreakpointObserver);

    protected readonly faCircleExclamation = faCircleExclamation;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faCode = faCode;

    private readonly resourceLoadingTriggered = signal(false);
    protected readonly showAlternatives = signal(false);

    readonly exerciseId = input.required<number>();
    readonly repositoryUri = input.required<string>();
    readonly vcsAccessTokenSourceInfo = input.required<VcsAccessTokenSourceInfo>();
    readonly repositoryViewLink = input.required<(string | number)[]>();
    readonly allowOnlineIde = input.required<boolean>();
    readonly programmingLanguage = input<ProgrammingLanguage>();
    readonly disabled = input(false);
    readonly size = input<CodeButtonSize>('default');
    readonly hideLabelMobile = input(false);
    readonly hideLabelBreakpoint = input<'md' | 'xl'>('md');

    private readonly theiaDisabled = computed(() => !this.allowOnlineIde() || !this.profileService.isModuleFeatureActive(MODULE_FEATURE_THEIA));
    private readonly matchesMediumBreakpoint = this.getMatchesBreakpointSignal(BREAKPOINTS.md);
    private readonly matchesExtraLargeBreakpoint = this.getMatchesBreakpointSignal(BREAKPOINTS.xl);
    protected readonly displayLabel = computed(() => this.computeDisplayLabel());
    protected readonly loadingState = computed(() => this.getLoadingState());

    private readonly userResource = resource({
        params: () => this.getUserResourceParams(),
        loader: () => this.loadUser(),
    });
    private readonly tokenResource = resource({
        params: () => this.getTokenResourceParams(),
        loader: ({ params }) => this.getToken(params.exerciseId, params.vcsAccessTokenData),
    });
    private readonly tokenUrlResource = resourceFromSnapshots(() => this.getTokenUrlSnapshot());
    private readonly authenticationMethodDataResource = resource({
        params: ({ chain }) => this.getAuthenticationMethodDataResourceParams(chain),
        loader: ({ params }) => {
            return this.loadAuthenticationMethodData(params.repositoryUri, params.login, params.tokenUrl);
        },
    });
    private readonly theiaLinkDataResource = resource({
        params: ({ chain }) => this.getTheiaLinkDataResourceParams(chain),
        loader: ({ params }) => this.loadTheiaLinkData(params.exerciseId, params.theiaPortalURL, params.repositoryUrl, params.userName, params.userEmail),
    });
    private readonly preferredIdeResource = resource({
        params: () => this.getPreferredIdeResourceParams(),
        loader: ({ params }) => this.loadPreferredIde(params.programmingLanguage),
    });

    private getMatchesBreakpointSignal(breakpoint: string) {
        return toSignal(this.breakpointObserver.observe(breakpoint).pipe(map((result) => result.matches)), {
            initialValue: this.breakpointObserver.isMatched(breakpoint),
        });
    }

    private computeDisplayLabel(): boolean {
        if (!this.hideLabelMobile()) {
            return true;
        }
        return this.hideLabelBreakpoint() === 'md' ? this.matchesMediumBreakpoint() : this.matchesExtraLargeBreakpoint();
    }

    private getLoadingState(): CodeButtonLoadingState {
        const anyRequiredResourceIdle =
            this.authenticationMethodDataResource.status() === 'idle' ||
            this.preferredIdeResource.status() === 'idle' ||
            (!this.theiaDisabled() && this.theiaLinkDataResource.status() === 'idle');
        const anyRequiredResourceLoading =
            this.authenticationMethodDataResource.isLoading() || this.preferredIdeResource.isLoading() || (!this.theiaDisabled() && this.theiaLinkDataResource.isLoading());
        const everyRequiredResourceHasValue =
            this.authenticationMethodDataResource.hasValue() && this.preferredIdeResource.hasValue() && (this.theiaDisabled() || this.theiaLinkDataResource.hasValue());

        if (anyRequiredResourceIdle) {
            return { status: 'idle' };
        } else if (anyRequiredResourceLoading) {
            return { status: 'loading' };
        } else if (everyRequiredResourceHasValue) {
            return {
                status: 'complete',
                authenticationMethodData: this.authenticationMethodDataResource.value(),
                preferredIde: this.preferredIdeResource.value(),
                theiaLinkData: this.theiaLinkDataResource.hasValue() ? this.theiaLinkDataResource.value() : undefined,
            };
        } else {
            return { status: 'error' };
        }
    }

    protected handleOpenChange(isOpen: boolean): void {
        if (isOpen) {
            this.resourceLoadingTriggered.set(true);
        } else {
            this.showAlternatives.set(false);
        }
    }

    private getUserResourceParams(): 'no-params' | undefined {
        return this.resourceLoadingTriggered() ? 'no-params' : undefined;
    }

    private async loadUser(): Promise<User> {
        const user = await this.accountService.identity();
        if (!user) {
            throw new Error('Incomplete user data');
        }
        return user;
    }

    private getTokenResourceParams() {
        if (!this.resourceLoadingTriggered()) {
            return undefined;
        }
        return {
            exerciseId: this.exerciseId(),
            vcsAccessTokenData: this.vcsAccessTokenSourceInfo(),
        };
    }

    private getAuthenticationMethodDataResourceParams(chain: ResourceParamsContext['chain']) {
        if (!this.resourceLoadingTriggered()) {
            return undefined;
        }
        const user = chain(this.userResource);
        if (!user) {
            throw new Error('Resolved user resource contained no user');
        }
        const login = user.login;
        if (!login) {
            throw new Error('Resolved user resource contained no login');
        }
        const tokenUrl = chain(this.tokenUrlResource);
        return {
            repositoryUri: this.repositoryUri(),
            login,
            tokenUrl,
        };
    }

    private getTheiaLinkDataResourceParams(chain: ResourceParamsContext['chain']) {
        if (!this.resourceLoadingTriggered()) {
            return undefined;
        }
        if (this.theiaDisabled()) {
            return undefined;
        }

        const theiaPortalURL = this.profileService.getProfileInfo()?.theiaPortalURL;
        const tokenUrl = chain(this.tokenUrlResource);
        const user = chain(this.userResource);
        const userName = user?.name;
        const userEmail = user?.email;
        if (!tokenUrl || !theiaPortalURL || !userName || !userEmail) {
            throw new Error('Incomplete Theia link data');
        }

        return {
            exerciseId: this.exerciseId(),
            theiaPortalURL,
            repositoryUrl: tokenUrl,
            userName,
            userEmail,
        };
    }

    private getPreferredIdeResourceParams() {
        if (!this.resourceLoadingTriggered()) {
            return undefined;
        }
        return { programmingLanguage: this.programmingLanguage() };
    }

    private async loadTheiaLinkData(exerciseId: number, theiaPortalURL: string, repositoryUrl: string, userName: string, userEmail: string): Promise<TheiaLinkData> {
        const theiaConfig = await firstValueFrom(this.programmingExerciseService.getTheiaConfig(exerciseId));
        if (!theiaConfig.theiaImage) {
            throw new Error('Incomplete Theia configuration');
        }
        return {
            theiaPortalURL,
            theiaImage: theiaConfig.theiaImage,
            repositoryUrl,
            userName,
            userEmail,
        };
    }

    private getTokenUrlSnapshot(): ResourceSnapshot<string | undefined> {
        const userSnapshot = this.userResource.snapshot();
        const tokenSnapshot = this.tokenResource.snapshot();

        if (userSnapshot.status === 'error') {
            return userSnapshot;
        }
        if (tokenSnapshot.status === 'error') {
            // Treats a failed token request as token authe being unavailable (important in case server is configured to not support token auth)
            return { status: 'resolved', value: undefined };
        }
        if (userSnapshot.status === 'loading' || userSnapshot.status === 'reloading' || tokenSnapshot.status === 'loading' || tokenSnapshot.status === 'reloading') {
            return { status: 'loading', value: undefined };
        }
        if (userSnapshot.status === 'idle' || tokenSnapshot.status === 'idle') {
            return { status: 'idle', value: undefined };
        }

        const login = userSnapshot.value?.login;
        const token = tokenSnapshot.value;
        if (!login || !token) {
            return { status: 'error', error: new Error('Incomplete token URL data') };
        }

        const credentials = `://${login}:${token}@`;
        const url = this.insertCredentialsIntoUrl(this.repositoryUri(), credentials);
        return { status: 'resolved', value: url };
    }

    private async loadAuthenticationMethodData(repositoryUri: string, login: string, tokenUrl: string | undefined): Promise<NonEmptyArray<AuthenticationMethodData>> {
        const authenticationMethods = this.getAuthenticationMethods();

        const authenticationMethodData = (
            await Promise.all(
                authenticationMethods.map((authenticationMethod) => {
                    return this.getAuthenticationMethodData(authenticationMethod, repositoryUri, login, tokenUrl);
                }),
            )
        ).filter(notUndefined);
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

    private async getAuthenticationMethodData(
        authenticationMethod: RepositoryAuthenticationMethod,
        repositoryUri: string,
        login: string,
        tokenUrl: string | undefined,
    ): Promise<AuthenticationMethodData | undefined> {
        switch (authenticationMethod) {
            case RepositoryAuthenticationMethod.SSH:
                return this.getSshAuthenticationMethodData(repositoryUri);
            case RepositoryAuthenticationMethod.Token:
                return this.getTokenAuthenticationMethodData(tokenUrl);
            case RepositoryAuthenticationMethod.Password:
                return this.getPasswordAuthenticationMethodData(repositoryUri, login);
        }
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

    private getTokenAuthenticationMethodData(tokenUrl: string | undefined): AuthenticationMethodData | undefined {
        if (!tokenUrl) {
            return undefined;
        }
        return {
            authenticationMethod: RepositoryAuthenticationMethod.Token,
            url: tokenUrl,
        };
    }

    private async getToken(exerciseId: number, vcsAccessTokenData: VcsAccessTokenSourceInfo): Promise<string> {
        switch (vcsAccessTokenData.returnedTokenType) {
            case 'participation-token':
                return this.loadOrCreateParticipationToken(vcsAccessTokenData.participationId);
            case 'repository-token':
                return this.loadOrCreateRepositoryToken(
                    exerciseId,
                    vcsAccessTokenData.repositoryType,
                    'auxiliaryRepositoryId' in vcsAccessTokenData ? vcsAccessTokenData.auxiliaryRepositoryId : undefined,
                    'participationId' in vcsAccessTokenData ? vcsAccessTokenData.participationId : undefined,
                );
        }
    }

    private loadOrCreateParticipationToken(participationId: number): Promise<string> {
        const loadRequest = () => {
            return this.accountService.getVcsAccessToken(participationId);
        };
        const createRequest = () => {
            return this.accountService.createVcsAccessToken(participationId);
        };
        return this.loadOrCreateToken(loadRequest, createRequest, 'participation');
    }

    private loadOrCreateRepositoryToken(
        exerciseId: number,
        repositoryType: RepositoryType,
        auxiliaryRepositoryId: number | undefined,
        participationId: number | undefined,
    ): Promise<string> {
        const loadRequest = () => {
            return this.programmingExerciseService.getRepositoryVcsAccessToken(exerciseId, repositoryType, auxiliaryRepositoryId, participationId);
        };
        const createRequest = () => {
            return this.programmingExerciseService.createRepositoryVcsAccessToken(exerciseId, repositoryType, auxiliaryRepositoryId, participationId);
        };
        return this.loadOrCreateToken(loadRequest, createRequest, 'repository');
    }

    private async loadOrCreateToken(
        loadRequest: () => Observable<HttpResponse<string>>,
        createRequest: () => Observable<HttpResponse<string>>,
        tokenType: 'participation' | 'repository',
    ): Promise<string> {
        try {
            return await this.requestToken(loadRequest, 'fetch', tokenType);
        } catch (error) {
            if (error instanceof HttpErrorResponse && error.status === 404) {
                return this.requestToken(createRequest, 'create', tokenType);
            }
            throw error;
        }
    }

    private async requestToken(request: () => Observable<HttpResponse<string>>, operation: 'fetch' | 'create', tokenType: 'participation' | 'repository'): Promise<string> {
        const response = await firstValueFrom(request());
        const token = response.body;
        if (!token) {
            throw new Error(`Unexpected error during valid ${operation} ${tokenType} token request: Server did not return token in response body.`);
        }
        return token;
    }

    private getPasswordAuthenticationMethodData(repositoryUri: string, login: string): AuthenticationMethodData {
        const credentials = `://${login}@`;
        const url = this.insertCredentialsIntoUrl(repositoryUri, credentials);

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
    private insertCredentialsIntoUrl(url: string, credentials: string): string {
        if (url.includes('@')) {
            return url.replace(/:\/\/.*@/, credentials);
        } else {
            return url.replace('://', credentials);
        }
    }

    private async loadPreferredIde(programmingLanguage: ProgrammingLanguage | undefined): Promise<Ide> {
        const idePreferences = await this.ideSettingsService.loadIdePreferences();
        const vsCodeFallback: Ide = { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' };

        let preferredIde: Ide | undefined;
        if (programmingLanguage) {
            preferredIde = idePreferences.get(programmingLanguage);
        }
        if (!preferredIde) {
            preferredIde = idePreferences.get(ProgrammingLanguage.EMPTY);
        }

        return preferredIde ?? vsCodeFallback;
    }
}
