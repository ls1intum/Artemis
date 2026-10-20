import { Component, type ResourceParamsContext, type ResourceSnapshot, computed, inject, input, resource, resourceFromSnapshots, signal } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleExclamation, faCircleNotch, faCode } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective } from '@tumaet/ui-angular';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AuthenticationMethodData, CodeButtonPopoverContentComponent, NonEmptyArray, SshKeyState, TheiaLinkData } from './code-button-popover-content.component';
import { type CodeButtonSize, RepositoryAuthenticationMethod, VcsAccessTokenData } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { SshUserSettingsService } from 'app/account/user/settings/ssh-settings/ssh-user-settings.service';
import dayjs from 'dayjs/esm';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { getSignalBasedOnRoute } from 'app/foundation/route/getSignalBasedOnRoute';
import { Router } from '@angular/router';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { Observable, firstValueFrom } from 'rxjs';
import { IdeSettingsService } from 'app/account/user/settings/ide-preferences/ide-settings.service';
import { Ide } from 'app/account/user/settings/ide-preferences/ide.model';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { User } from 'app/account/user/user.model';
import { MODULE_FEATURE_THEIA } from 'app/app.constants';

type CodeButtonLoadingState =
    | { status: 'loading' }
    | { status: 'error' }
    | { status: 'complete'; authenticationMethodData: NonEmptyArray<AuthenticationMethodData>; preferredIde: Ide; theiaLinkData: TheiaLinkData | undefined };

@Component({
    selector: 'jhi-code-button-new',
    templateUrl: './code-button-new.component.html',
    imports: [TumAetUiButtonDirective, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective, FaIconComponent, ArtemisTranslatePipe, CodeButtonPopoverContentComponent],
})
export class CodeButtonNewComponent {
    private readonly accountService = inject(AccountService);
    private readonly profileService = inject(ProfileService);
    private readonly sshUserSettingsService = inject(SshUserSettingsService);
    private readonly programmingExerciseService = inject(ProgrammingExerciseService);
    private readonly ideSettingsService = inject(IdeSettingsService);
    private readonly router = inject(Router);

    protected readonly faCircleExclamation = faCircleExclamation;
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faCode = faCode;
    protected readonly showAlternatives = signal(false);

    private readonly isInCourseManagement = getSignalBasedOnRoute(this.router, (url) => url.includes('course-management'));

    readonly exerciseId = input.required<number>();
    readonly repositoryUri = input.required<string>();
    readonly vcsAccessTokenData = input.required<VcsAccessTokenData>();
    readonly repositoryViewLink = input.required<(string | number)[]>();
    readonly allowOnlineIde = input.required<boolean>();
    readonly programmingLanguage = input<ProgrammingLanguage>();
    readonly disabled = input(false);
    readonly size = input<CodeButtonSize>('default');

    private readonly theiaDisabled = computed(() => !this.allowOnlineIde() || !this.profileService.isModuleFeatureActive(MODULE_FEATURE_THEIA));
    protected readonly loadingState = computed(() => this.getLoadingState());

    private readonly userResource = resource({
        loader: () => this.loadUser(),
    });
    private readonly tokenResource = resource({
        params: () => ({
            exerciseId: this.exerciseId(),
            isInCourseManagement: this.isInCourseManagement(),
            vcsAccessTokenData: this.vcsAccessTokenData(),
        }),
        loader: ({ params }) => this.getToken(params.exerciseId, params.isInCourseManagement, params.vcsAccessTokenData),
    });
    private readonly tokenUrlResource = resourceFromSnapshots(() => this.getTokenUrlSnapshot());
    private readonly authenticationMethodDataResource = resource({
        params: ({ chain }) => this.getAuthenticationMethodDataParams(chain),
        loader: ({ params }) => {
            return this.loadAuthenticationMethodData(params.repositoryUri, params.login, params.tokenUrl);
        },
    });
    private readonly theiaLinkDataResource = resource({
        params: ({ chain }) => this.getTheiaLinkDataParams(chain),
        loader: ({ params }) => this.loadTheiaLinkData(params.exerciseId, params.theiaPortalURL, params.repositoryUrl, params.userName, params.userEmail),
    });
    private readonly preferredIdeResource = resource({
        params: () => ({ programmingLanguage: this.programmingLanguage() }),
        loader: ({ params }) => this.loadPreferredIde(params.programmingLanguage),
    });

    private getLoadingState(): CodeButtonLoadingState {
        if (this.authenticationMethodDataResource.isLoading() || this.theiaLinkDataResource.isLoading() || this.preferredIdeResource.isLoading()) {
            return { status: 'loading' };
        } else if (this.authenticationMethodDataResource.hasValue() && this.preferredIdeResource.hasValue() && (this.theiaDisabled() || this.theiaLinkDataResource.hasValue())) {
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
        if (!isOpen) {
            this.showAlternatives.set(false);
        }
    }

    private async loadUser(): Promise<User> {
        const user = await this.accountService.identity();
        if (!user) {
            throw new Error('Incomplete user data');
        }
        return user;
    }

    private getAuthenticationMethodDataParams(chain: ResourceParamsContext['chain']) {
        const user = chain(this.userResource);
        if (!user) {
            throw new Error('Resolved user resource contained no user');
        }
        const login = user.login;
        if (!login) {
            throw new Error('Resolved user resource contained no login');
        }
        const tokenUrl = chain(this.tokenUrlResource);
        if (!tokenUrl) {
            throw new Error('Resolved token URL resource contained no URL');
        }
        return {
            repositoryUri: this.repositoryUri(),
            login,
            tokenUrl,
        };
    }

    private getTheiaLinkDataParams(chain: ResourceParamsContext['chain']) {
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

        if (userSnapshot.status === 'error' || tokenSnapshot.status === 'error') {
            return userSnapshot.status === 'error' ? userSnapshot : tokenSnapshot;
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

    private async loadAuthenticationMethodData(repositoryUri: string, login: string, tokenUrl: string): Promise<NonEmptyArray<AuthenticationMethodData>> {
        const authenticationMethods = this.getAuthenticationMethods();

        const authenticationMethodData = await Promise.all(
            authenticationMethods.map((authenticationMethod) => {
                return this.getAuthenticationMethodData(authenticationMethod, repositoryUri, login, tokenUrl);
            }),
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

    private async getAuthenticationMethodData(
        authenticationMethod: RepositoryAuthenticationMethod,
        repositoryUri: string,
        login: string,
        tokenUrl: string,
    ): Promise<AuthenticationMethodData> {
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

    private getTokenAuthenticationMethodData(tokenUrl: string): AuthenticationMethodData {
        return {
            authenticationMethod: RepositoryAuthenticationMethod.Token,
            url: tokenUrl,
        };
    }

    private async getToken(exerciseId: number, isInCourseManagement: boolean, vcsAccessTokenData: VcsAccessTokenData): Promise<string> {
        switch (vcsAccessTokenData.type) {
            case 'participation':
                if (!isInCourseManagement || vcsAccessTokenData.isOwnParticipation) {
                    return this.loadOrCreateParticipationToken(vcsAccessTokenData.participationId);
                } else {
                    throw new Error('Invalid token fetch attempt: Tried to fetch token for participation in course-management of which the user was no owner.');
                }
            case 'student-repository':
                return this.loadOrCreateRepositoryToken(exerciseId, RepositoryType.USER, undefined, vcsAccessTokenData.participationId);
            case 'base-repository':
                return this.loadOrCreateRepositoryToken(exerciseId, vcsAccessTokenData.repositoryType, undefined, undefined);
            case 'auxiliary-repository':
                return this.loadOrCreateRepositoryToken(exerciseId, RepositoryType.AUXILIARY, vcsAccessTokenData.auxiliaryRepositoryId, undefined);
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
