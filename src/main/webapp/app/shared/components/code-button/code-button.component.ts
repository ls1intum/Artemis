import { Component, OnInit, effect, inject, input, signal } from '@angular/core';
import { ProgrammingExercise, ProgrammingLanguage } from 'app/entities/programming/programming-exercise.model';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { ExternalCloningService } from 'app/exercises/programming/shared/service/external-cloning.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { LocalStorageService } from 'ngx-webstorage';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { PROFILE_GITLAB, PROFILE_LOCALVC } from 'app/app.constants';
import dayjs from 'dayjs/esm';
import { isPracticeMode } from 'app/entities/participation/student-participation.model';
import { faCode, faExternalLink } from '@fortawesome/free-solid-svg-icons';
import { IdeSettingsService } from 'app/shared/user-settings/ide-preferences/ide-settings.service';
import { Ide } from 'app/shared/user-settings/ide-preferences/ide.model';
import { SshUserSettingsService } from 'app/shared/user-settings/ssh-settings/ssh-user-settings.service';
import { UserSshPublicKey } from 'app/entities/programming/user-ssh-public-key.model';
import { ExerciseActionButtonComponent } from '../exercise-action-button.component';
import { FeatureToggleDirective } from '../../feature-toggle/feature-toggle.directive';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle, NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { CdkCopyToClipboard } from '@angular/cdk/clipboard';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Router, RouterLink } from '@angular/router';
import { HelpIconComponent } from '../help-icon.component';
import { ArtemisTranslatePipe } from '../../pipes/artemis-translate.pipe';
import { SafeUrlPipe } from 'app/shared/pipes/safe-url.pipe';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { AlertService } from 'app/core/util/alert.service';

export enum States {
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
    private localStorage = inject(LocalStorageService);
    private participationService = inject(ParticipationService);
    private ideSettingsService = inject(IdeSettingsService);
    private alertService = inject(AlertService);
    private router = inject(Router);

    readonly FeatureToggle = FeatureToggle;
    readonly ProgrammingLanguage = ProgrammingLanguage;

    loading = input<boolean>(false);

    // either use the participation token (true) OR the user token (false)
    smallButtons = input.required<boolean>();
    repositoryUri = input.required<string>();
    routerLinkForRepositoryView = input.required<(string | number)[]>();
    participations = input<ProgrammingExerciseStudentParticipation[]>([]);
    exercise = input<ProgrammingExercise>();
    hideLabelMobile = input<boolean>(false);

    // this is the fallback
    authenticationMechanism = [States.Password, States.Token, States.SSH];
    currentState = States.Password;

    userTokenStillValid = false;
    userTokenPresent = false;

    sshEnabled = false;
    sshTemplateUrl?: string;
    versionControlUrl: string;

    localVCEnabled = signal<boolean>(false);
    gitlabVCEnabled = false;

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
            if (!this.isInCourseManagement && this.localVCEnabled()) {
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
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.sshSettingsUrl = profileInfo.sshKeysURL;
            this.sshTemplateUrl = profileInfo.sshCloneURLTemplate;

            if (profileInfo.authenticationMechanisms?.length) {
                this.authenticationMechanism = profileInfo.authenticationMechanisms.filter((method): method is States => Object.values(States).includes(method as States));
            }
            if (profileInfo.versionControlUrl) {
                this.versionControlUrl = profileInfo.versionControlUrl;
            }

            this.localVCEnabled.set(profileInfo.activeProfiles.includes(PROFILE_LOCALVC));
            this.gitlabVCEnabled = profileInfo.activeProfiles.includes(PROFILE_GITLAB);

            this.configureTooltips(profileInfo);
        });

        this.ideSettingsService.loadIdePreferences().then((programmingLanguageToIde) => {
            if (programmingLanguageToIde.size) {
                this.programmingLanguageToIde = programmingLanguageToIde;
            }
            this.ideName = this.getIde().name;
        });
    }

    public useSshUrl() {
        this.currentState = States.SSH;

        this.copyEnabled = this.doesUserHaveSSHkeys || this.gitlabVCEnabled;
        this.storeToLocalStorage();
    }

    public useHttpsToken() {
        this.currentState = States.Token;

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
        this.currentState = States.Password;

        this.copyEnabled = true;
        this.storeToLocalStorage();
    }

    private storeToLocalStorage() {
        this.localStorage.store('code-button-state', this.currentState);
    }

    public formatTip(translationKey: string, url: string): string {
        return this.translateService.instant(translationKey).replace(/{link:(.*)}/, `<a href="${url}" target="_blank">$1</a>`);
    }

    private getRepositoryUri() {
        return this.activeParticipation?.repositoryUri ?? this.repositoryUri();
    }

    onClick() {
        this.currentState = this.localStorage.retrieve('code-button-state') || States.Password;

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
     * @param url the url to which the credentials should be added
     * @param insertPlaceholder if true, instead of the actual token, '**********' is used (e.g. to prevent leaking the token during a screen-share)
     */
    getHttpOrSshRepositoryUri(insertPlaceholder = true): string {
        if (this.useSsh && this.sshTemplateUrl) {
            return this.getSshCloneUrl(this.getRepositoryUri());
        }
        const url = this.getRepositoryUri();
        const token = insertPlaceholder ? '**********' : this.getUsedToken();

        const credentials = `://${this.user.login}${this.useToken ? `:${token}` : ''}@`;

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

    private getUsedToken(): string | undefined {
        if (this.useToken) {
            if (this.isInCourseManagement) {
                return this.user.vcsAccessToken;
            } else {
                return this.activeParticipation?.vcsAccessToken;
            }
        }
        return '';
    }

    /**
     * Gets the external link of the repository. For LocalVC, undefined is returned.
     */
    getHttpRepositoryUri(): string {
        return this.isTeamParticipation ? this.repositoryUriForTeam(this.getRepositoryUri()) : this.getRepositoryUri();
    }

    /**
     * The user info part of the repository uri of a team participation has to be added with the current user's login.
     *
     * @return repository uri with username of current user inserted
     */
    private repositoryUriForTeam(url: string) {
        // (https://)(gitlab.ase.in.tum.de/...-team1.git)  =>  (https://)ga12abc@(gitlab.ase.in.tum.de/...-team1.git)
        return url.replace(/^(\w*:\/\/)(.*)$/, `$1${this.user.login}@$2`);
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
        return this.currentState === States.Token;
    }

    get useSsh() {
        return this.currentState === States.SSH;
    }

    get usePassword() {
        return this.currentState === States.Password;
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

    private configureTooltips(profileInfo: ProfileInfo) {
        if (this.localVCEnabled()) {
            this.vcsTokenSettingsUrl = `${window.location.origin}/user-settings/vcs-token`;
            this.sshSettingsUrl = `${window.location.origin}/user-settings/ssh`;
        } else {
            this.sshSettingsUrl = profileInfo.sshKeysURL;
        }
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

    protected readonly States = States;
}
