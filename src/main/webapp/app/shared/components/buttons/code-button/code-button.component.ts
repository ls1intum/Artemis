import { Component, OnInit, effect, inject, input } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ExternalCloningService } from 'app/programming/shared/services/external-cloning.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { PROFILE_THEIA } from 'app/app.constants';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import dayjs from 'dayjs/esm';
import { isPracticeMode } from 'app/exercise/shared/entities/participation/student-participation.model';
import { faCode, faExternalLink } from '@fortawesome/free-solid-svg-icons';
import { UserSshPublicKey } from 'app/programming/shared/entities/user-ssh-public-key.model';
import { ExerciseActionButtonComponent } from 'app/shared/components/buttons/exercise-action-button/exercise-action-button.component';
import { FeatureToggleDirective } from '../../../feature-toggle/feature-toggle.directive';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle, NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { CdkCopyToClipboard } from '@angular/cdk/clipboard';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Router, RouterLink } from '@angular/router';
import { HelpIconComponent } from '../../help-icon/help-icon.component';
import { ArtemisTranslatePipe } from '../../../pipes/artemis-translate.pipe';
import { SafeUrlPipe } from 'app/shared/pipes/safe-url.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertService } from 'app/shared/service/alert.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { TheiaService } from 'app/programming/shared/services/theia.service';
import { SshUserSettingsService } from 'app/core/user/settings/ssh-settings/ssh-user-settings.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { IdeSettingsService } from 'app/core/user/settings/ide-preferences/ide-settings.service';
import { Ide } from 'app/core/user/settings/ide-preferences/ide.model';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';

export enum RepositoryAuthenticationMethod {
    Password = 'password',
    Token = 'token',
    SSH = 'ssh',
}

@Component({
    selector: 'jhi-code-button',
    templateUrl: './code-button.component.html',
    styleUrls: ['./code-button.component.scss'],
    imports: [
        ExerciseActionButtonComponent,
        FeatureToggleDirective,
        NgbPopover,
        TranslateDirective,
        NgbDropdown,
        NgbDropdownToggle,
        NgbDropdownMenu,
        CdkCopyToClipboard,
        FaIconComponent,
        RouterLink,
        HelpIconComponent,
        ArtemisTranslatePipe,
        SafeUrlPipe,
    ],
})
export class CodeButtonComponent implements OnInit {
    private translateService = inject(TranslateService);
    private externalCloningService = inject(ExternalCloningService);
    private sshUserSettingsService = inject(SshUserSettingsService);
    private accountService = inject(AccountService);
    private profileService = inject(ProfileService);
    private localStorageService = inject(LocalStorageService);
    private participationService = inject(ParticipationService);
    private ideSettingsService = inject(IdeSettingsService);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private alertService = inject(AlertService);
    private theiaService = inject(TheiaService);
    private router = inject(Router);

    protected readonly FeatureToggle = FeatureToggle;
    protected readonly ProgrammingLanguage = ProgrammingLanguage;
    protected readonly RepositoryAuthenticationMethod = RepositoryAuthenticationMethod;

    loading = input<boolean>(false);

    // either use the participation token (true) OR the user token (false)
    smallButtons = input.required<boolean>();
    repositoryUri = input.required<string>();
    routerLinkForRepositoryView = input.required<(string | number)[]>();
    participations = input<ProgrammingExerciseStudentParticipation[]>([]);
    exercise = input<ProgrammingExercise>();
    hideLabelMobile = input<boolean>(false);

    // this is the fallback with a default order in case the server does not specify this as part of the profile info endpoint
    authenticationMechanisms = [RepositoryAuthenticationMethod.Password, RepositoryAuthenticationMethod.Token, RepositoryAuthenticationMethod.SSH];
    selectedAuthenticationMechanism = RepositoryAuthenticationMethod.Password;

    userTokenStillValid = false;
    userTokenPresent = false;

    sshEnabled = false;
    sshTemplateUrl?: string;
    versionControlUrl: string;

    copyEnabled = false;
    doesUserHaveSSHkeys = false;
    areAnySshKeysExpired = false;
    isInCourseManagement = false;

    sshSettingsUrl: string;
    vcsTokenSettingsUrl: string;
    sshKeyMissingTip: string;
    sshKeysExpiredTip: string;
    tokenMissingTip: string;
    tokenExpiredTip: string;
    user: User;
    sshKeys?: UserSshPublicKey[];
    cloneHeadline: string;
    wasCopied = false;
    isTeamParticipation: boolean;
    activeParticipation?: ProgrammingExerciseStudentParticipation;
    isPracticeMode: boolean | undefined;

    vscodeFallback: Ide = { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' };
    programmingLanguageToIde: Map<ProgrammingLanguage, Ide> = new Map([[ProgrammingLanguage.EMPTY, this.vscodeFallback]]);

    theiaEnabled = false;
    theiaPortalURL: string;

    // Icons
    readonly faCode = faCode;
    readonly faExternalLink = faExternalLink;
    ideName: string;

    constructor() {
        this.isInCourseManagement = this.router.url.includes('course-management');

        effect(async () => {
            if (this.participations().length) {
                const shouldPreferPractice = this.participationService.shouldPreferPractice(this.exercise());
                this.activeParticipation = this.participationService.getSpecificStudentParticipation(this.participations(), shouldPreferPractice) ?? this.participations()[0];
                this.isPracticeMode = isPracticeMode(this.activeParticipation);
                this.isTeamParticipation = !!this.activeParticipation?.team;
            }

            this.cloneHeadline = this.getCloneHeadline();
        });

        effect(() => {
            if (!this.isInCourseManagement) {
                this.loadVcsAccessTokensForAllParticipations();
            }
        });
    }

    async ngOnInit() {
        const user = await this.accountService.identity();
        if (!user) {
            return;
        }
        this.user = user;

        await this.checkForSshKeys();

        // Get ssh information from the user
        const profileInfo = this.profileService.getProfileInfo();
        this.sshTemplateUrl = profileInfo.sshCloneURLTemplate;

        if (profileInfo.repositoryAuthenticationMechanisms?.length) {
            this.authenticationMechanisms = profileInfo.repositoryAuthenticationMechanisms.filter((method): method is RepositoryAuthenticationMethod =>
                Object.values(RepositoryAuthenticationMethod).includes(method as RepositoryAuthenticationMethod),
            );
        }
        if (profileInfo.versionControlUrl) {
            this.versionControlUrl = profileInfo.versionControlUrl;
        }

        this.configureTooltips();
        this.initTheia(profileInfo);

        this.ideSettingsService.loadIdePreferences().then((programmingLanguageToIde) => {
            if (programmingLanguageToIde.size) {
                this.programmingLanguageToIde = programmingLanguageToIde;
            }
            this.ideName = this.getIde().name;
        });
    }

    public useSshUrl() {
        this.selectedAuthenticationMechanism = RepositoryAuthenticationMethod.SSH;

        this.copyEnabled = this.doesUserHaveSSHkeys;
        this.storeToLocalStorage();
    }

    public useHttpsToken() {
        this.selectedAuthenticationMechanism = RepositoryAuthenticationMethod.Token;

        if (this.isInCourseManagement) {
            this.userTokenStillValid = dayjs().isBefore(dayjs(this.user.vcsAccessTokenExpiryDate));
            this.userTokenPresent = !!this.user.vcsAccessToken?.startsWith('vcpat');
            this.copyEnabled = this.userTokenPresent && this.userTokenStillValid;
        } else {
            this.copyEnabled = !!this.activeParticipation?.vcsAccessToken;
        }
        this.storeToLocalStorage();
    }

    public useHttpsPassword() {
        this.selectedAuthenticationMechanism = RepositoryAuthenticationMethod.Password;

        this.copyEnabled = true;
        this.storeToLocalStorage();
    }

    private storeToLocalStorage() {
        this.localStorageService.store<RepositoryAuthenticationMethod>('code-button-state', this.selectedAuthenticationMechanism);
    }

    public formatTip(translationKey: string, url: string): string {
        return this.translateService.instant(translationKey).replace(/{link:(.*)}/, `<a href="${url}" target="_blank">$1</a>`);
    }

    private getRepositoryUri() {
        return this.activeParticipation?.repositoryUri ?? this.repositoryUri();
    }

    onClick() {
        const storedState = this.localStorageService.retrieve<RepositoryAuthenticationMethod>('code-button-state');
        this.selectedAuthenticationMechanism = storedState && this.authenticationMechanisms.includes(storedState) ? storedState : this.authenticationMechanisms[0];

        if (this.useSsh) {
            this.useSshUrl();
        }
        if (this.useToken) {
            this.useHttpsToken();
        }
        if (this.usePassword) {
            this.useHttpsPassword();
        }
    }
    /**
     * Add the credentials to the http url, if a token should be used.
     *
     * @param insertPlaceholder if true, instead of the actual token, '**********' is used (e.g. to prevent leaking the token during a screen-share)
     * @param alwaysUsetoken if true, the token authentication method is always used, even if the user has not selected to use it
     * @param alwaysReturnHttp if true, the http url is always returned, even if ssh is selected
     */
    getHttpOrSshRepositoryUri(insertPlaceholder = true, alwaysUsetoken = false, alwaysReturnHttp = false): string {
        if (!alwaysReturnHttp && this.useSsh && this.sshTemplateUrl) {
            return this.getSshCloneUrl(this.getRepositoryUri());
        }
        const url = this.getRepositoryUri();
        const token = insertPlaceholder ? '**********' : this.getUsedToken(alwaysUsetoken);

        const credentials = `://${this.user.login}${this.useToken || alwaysUsetoken ? `:${token}` : ''}@`;

        if (!url.includes('@')) {
            // the url has the format https://vcs-server.com
            return url.replace('://', credentials);
        } else {
            // the url has the format https://username@vcs-server.com -> replace ://username@
            return url.replace(/:\/\/.*@/, credentials);
        }
    }

    loadVcsAccessTokensForAllParticipations() {
        this.participations().forEach((participation) => {
            if (participation.id && !participation.vcsAccessToken) {
                this.loadParticipationVcsAccessToken(participation);
            }
        });
    }

    /**
     * Loads the vcsAccessToken for a participation from the server. If none exists, sends a request to create one
     * (Usually the token exists, as it is created when the server creates the participation)
     */
    loadParticipationVcsAccessToken(participation: ProgrammingExerciseStudentParticipation) {
        this.accountService.getVcsAccessToken(participation!.id!).subscribe({
            next: (res: HttpResponse<string>) => {
                if (res.body) {
                    participation.vcsAccessToken = res.body;
                    this.copyEnabled = this.useToken;
                }
            },
            error: (error: HttpErrorResponse) => {
                if (error.status == 404) {
                    this.createNewParticipationVcsAccessToken(participation);
                }
                if (error.status == 403) {
                    this.alertService.warning('403 Forbidden');
                }
            },
        });
    }

    /**
     * Sends the request to create a new participation VCS access token
     */
    createNewParticipationVcsAccessToken(participation: ProgrammingExerciseStudentParticipation) {
        this.accountService.createVcsAccessToken(participation!.id!).subscribe({
            next: (res: HttpResponse<string>) => {
                if (res.body) {
                    participation.vcsAccessToken = res.body;
                    this.copyEnabled = this.useToken;
                }
            },
            error: (error: HttpErrorResponse) => {
                if (error.status == 403) {
                    this.alertService.warning('403 Forbidden');
                }
            },
        });
    }

    private getUsedToken(alwaysUseToken = false): string | undefined {
        if (this.useToken || alwaysUseToken) {
            if (this.isInCourseManagement) {
                return this.user.vcsAccessToken;
            } else {
                return this.activeParticipation?.vcsAccessToken;
            }
        }
        return '';
    }

    /**
     * Transforms the repository uri to an ssh clone url
     */
    private getSshCloneUrl(url: string) {
        return url.replace(/^\w*:\/\/[^/]*?\/(scm\/)?(.*)$/, this.sshTemplateUrl + '$2');
    }

    /**
     * set wasCopied for 3 seconds on success
     */
    onCopyFinished(successful: boolean) {
        if (successful) {
            this.wasCopied = true;
            setTimeout(() => {
                this.wasCopied = false;
            }, 3000);
        }
    }

    /**
     * build the sourceTreeUrl from the repository uri
     * @return sourceTreeUrl
     */
    buildSourceTreeUrl(): string | undefined {
        return this.externalCloningService.buildSourceTreeUrl(this.versionControlUrl, this.getHttpOrSshRepositoryUri(false));
    }

    buildIdeUrl(): string | undefined {
        return this.externalCloningService.buildIdeUrl(this.getHttpOrSshRepositoryUri(false), this.getIde());
    }

    getIde(): Ide {
        return (
            this.programmingLanguageToIde.get(this.exercise()?.programmingLanguage ?? ProgrammingLanguage.EMPTY) ??
            this.programmingLanguageToIde.get(ProgrammingLanguage.EMPTY) ??
            this.vscodeFallback
        );
    }

    switchPracticeMode() {
        this.isPracticeMode = !this.isPracticeMode;
        this.activeParticipation = this.participationService.getSpecificStudentParticipation(this.participations(), this.isPracticeMode)!;
        this.cloneHeadline = this.isPracticeMode ? 'artemisApp.exerciseActions.clonePracticeRepository' : 'artemisApp.exerciseActions.cloneRatedRepository';
        if (this.activeParticipation.vcsAccessToken) {
            this.user.vcsAccessToken = this.activeParticipation.vcsAccessToken;
        }
    }

    get useToken() {
        return this.selectedAuthenticationMechanism === RepositoryAuthenticationMethod.Token;
    }

    get useSsh() {
        return this.selectedAuthenticationMechanism === RepositoryAuthenticationMethod.SSH;
    }

    get usePassword() {
        return this.selectedAuthenticationMechanism === RepositoryAuthenticationMethod.Password;
    }
    /**
     * Checks whether the user owns any SSH keys, and checks if any of them is expired
     */
    private async checkForSshKeys() {
        this.sshKeys = await this.sshUserSettingsService.getCachedSshKeys();
        if (this.sshKeys) {
            const now = dayjs();
            this.doesUserHaveSSHkeys = this.sshKeys.length > 0;
            this.areAnySshKeysExpired = this.sshKeys.some((key) => {
                if (key.expiryDate) {
                    return dayjs(key.expiryDate).isBefore(now);
                }
            });
        }
    }

    private configureTooltips() {
        this.vcsTokenSettingsUrl = `${window.location.origin}/user-settings/vcs-token`;
        this.sshSettingsUrl = `${window.location.origin}/user-settings/ssh`;
        this.tokenMissingTip = this.formatTip('artemisApp.exerciseActions.vcsTokenTip', this.vcsTokenSettingsUrl);
        this.tokenExpiredTip = this.formatTip('artemisApp.exerciseActions.vcsTokenExpiredTip', this.vcsTokenSettingsUrl);
        this.sshKeyMissingTip = this.formatTip('artemisApp.exerciseActions.sshKeyTip', this.sshSettingsUrl);
        this.sshKeysExpiredTip = this.formatTip('artemisApp.exerciseActions.sshKeyExpiredTip', this.sshSettingsUrl);
    }

    private getCloneHeadline() {
        if (this.participations().length) {
            this.isPracticeMode = isPracticeMode(this.activeParticipation);
            return this.isPracticeMode && !this.exercise()?.exerciseGroup
                ? 'artemisApp.exerciseActions.clonePracticeRepository'
                : 'artemisApp.exerciseActions.cloneRatedRepository';
        } else {
            return 'artemisApp.exerciseActions.cloneExerciseRepository';
        }
    }

    private initTheia(profileInfo: ProfileInfo) {
        if (this.profileService.isProfileActive(PROFILE_THEIA) && this.exercise()) {
            const exercise = this.exercise()!;
            // Theia requires the Build Config of the programming exercise to be set
            this.programmingExerciseService.getTheiaConfig(exercise.id!).subscribe((theiaConfig) => {
                // Merge the theiaConfig (containing the theiaImage) into the buildConfig
                this.exercise()!.buildConfig = { ...exercise.buildConfig!, ...theiaConfig };

                // Set variables now, sanitize later on
                this.theiaPortalURL = profileInfo.theiaPortalURL ?? '';

                // Verify that all conditions are met
                if (this.theiaPortalURL !== '' && exercise.allowOnlineIde && theiaConfig.theiaImage) {
                    this.theiaEnabled = true;
                }
            });
        }
    }

    async startOnlineIDE() {
        const theiaImage = this.exercise()?.buildConfig?.theiaImage ?? '';
        const repositoryUri = this.getHttpOrSshRepositoryUri(false, true, true);
        const userName = this.user.name;
        const userEmail = this.user.email;

        await this.theiaService.startOnlineIDE(this.theiaPortalURL, theiaImage, repositoryUri, userName, userEmail);
    }
}
