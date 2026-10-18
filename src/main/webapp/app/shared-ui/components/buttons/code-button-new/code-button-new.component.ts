import { Component, type ResourceParamsContext, computed, inject, input, resource } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faCircleExclamation, faCircleNotch, faCode } from '@fortawesome/free-solid-svg-icons';
import { TumAetUiButtonDirective, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective } from '@tumaet/ui-angular';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { AuthenticationMethodData, CodeButtonPopoverContentComponent, NonEmptyArray, SshKeyState } from './code-button-popover-content.component';
import { RepositoryAuthenticationMethod, VcsAccessTokenData } from 'app/shared-ui/components/buttons/code-button/code-button.component';
import { SshUserSettingsService } from 'app/account/user/settings/ssh-settings/ssh-user-settings.service';
import dayjs from 'dayjs/esm';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { getSignalBasedOnRoute } from 'app/foundation/route/getSignalBasedOnRoute';
import { Router } from '@angular/router';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { Observable } from 'rxjs';
import { IdeSettingsService } from 'app/account/user/settings/ide-preferences/ide-settings.service';
import { Ide } from 'app/account/user/settings/ide-preferences/ide.model';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { User } from 'app/account/user/user.model';

type CodeButtonLoadingState =
    { status: 'loading' } | { status: 'error' } | { status: 'complete'; authenticationMethodData: NonEmptyArray<AuthenticationMethodData>; preferredIde: Ide };

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

    private readonly isInCourseManagement = getSignalBasedOnRoute(this.router, (url) => url.includes('course-management'));

    readonly exerciseId = input.required<number>();
    readonly repositoryUri = input.required<string>();
    readonly vcsAccessTokenData = input.required<VcsAccessTokenData>();
    readonly repositoryViewLink = input.required<(string | number)[]>();
    readonly programmingLanguage = input<ProgrammingLanguage>();

    protected readonly loadingState = computed(() => this.getLoadingState());

    private readonly userResource = resource({ loader: () => this.loadUser() });

    private readonly tokenResource = resource({
        params: () => ({
            exerciseId: this.exerciseId(),
            isInCourseManagement: this.isInCourseManagement(),
            vcsAccessTokenData: this.vcsAccessTokenData(),
        }),
        loader: ({ params }) => this.getToken(params.exerciseId, params.isInCourseManagement, params.vcsAccessTokenData),
    });

    private readonly authenticationMethodDataResource = resource({
        params: ({ chain }) => this.getAuthenticationMethodDataParams(chain),
        loader: ({ params }) => {
            return this.loadAuthenticationMethodData(params.repositoryUri, params.login, params.token);
        },
    });

    private readonly preferredIdeResource = resource({
        params: () => ({ programmingLanguage: this.programmingLanguage() }),
        loader: ({ params }) => this.loadPreferredIde(params.programmingLanguage),
    });

    private getLoadingState(): CodeButtonLoadingState {
        if (this.authenticationMethodDataResource.isLoading() || this.preferredIdeResource.isLoading()) {
            return { status: 'loading' };
        } else if (this.authenticationMethodDataResource.hasValue() && this.preferredIdeResource.hasValue()) {
            return {
                status: 'complete',
                authenticationMethodData: this.authenticationMethodDataResource.value(),
                preferredIde: this.preferredIdeResource.value(),
            };
        } else {
            return { status: 'error' };
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
        const token = chain(this.tokenResource);
        if (!token) {
            throw new Error('Resolved token resource contained no token');
        }
        return {
            repositoryUri: this.repositoryUri(),
            login: user.login,
            token,
        };
    }

    private async loadAuthenticationMethodData(repositoryUri: string, login: string | undefined, token: string): Promise<NonEmptyArray<AuthenticationMethodData>> {
        const authenticationMethods = this.getAuthenticationMethods();

        const authenticationMethodData = await Promise.all(
            authenticationMethods.map((authenticationMethod) => {
                return this.getAuthenticationMethodData(authenticationMethod, repositoryUri, login, token);
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
        login: string | undefined,
        token: string,
    ): Promise<AuthenticationMethodData> {
        switch (authenticationMethod) {
            case RepositoryAuthenticationMethod.SSH:
                return this.getSshAuthenticationMethodData(repositoryUri);
            case RepositoryAuthenticationMethod.Token:
                return this.getTokenAuthenticationMethodData(repositoryUri, this.requireLogin(login), token);
            case RepositoryAuthenticationMethod.Password:
                return this.getPasswordAuthenticationMethodData(repositoryUri, this.requireLogin(login));
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

    private requireLogin(login: string | undefined): string {
        if (!login) {
            throw new Error('Incomplete token or password authentication data');
        }
        return login;
    }

    private getTokenAuthenticationMethodData(repositoryUri: string, login: string, token: string): AuthenticationMethodData {
        const credentials = `://${login}:${token}@`;
        const url = this.insertCredentialsIntoUrl(repositoryUri, credentials);

        return {
            authenticationMethod: RepositoryAuthenticationMethod.Token,
            url,
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

    private requestToken(request: () => Observable<HttpResponse<string>>, operation: 'fetch' | 'create', tokenType: 'participation' | 'repository'): Promise<string> {
        return new Promise((resolve, reject) => {
            request().subscribe({
                next: (response) => {
                    const token = response.body;
                    if (!token) {
                        reject(new Error(`Unexpected error during valid ${operation} ${tokenType} token request: Server did not return token in response body.`));
                        return;
                    }
                    resolve(token);
                },
                error: reject,
            });
        });
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
