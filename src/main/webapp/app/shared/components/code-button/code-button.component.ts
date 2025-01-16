import { Component, Input, OnChanges, OnInit, inject } from '@angular/core';
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
import { PROFILE_GITLAB, PROFILE_LOCALVC, PROFILE_THEIA } from 'app/app.constants';
import dayjs from 'dayjs/esm';
import { isPracticeMode } from 'app/entities/participation/student-participation.model';
import { faCode, faDesktop, faExternalLink } from '@fortawesome/free-solid-svg-icons';
import { IdeSettingsService } from 'app/shared/user-settings/ide-preferences/ide-settings.service';
import { Ide } from 'app/shared/user-settings/ide-preferences/ide.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { SshUserSettingsService } from 'app/shared/user-settings/ssh-settings/ssh-user-settings.service';
import { UserSshPublicKey } from 'app/entities/programming/user-ssh-public-key.model';
import { ExerciseActionButtonComponent } from '../exercise-action-button.component';
import { FeatureToggleDirective } from '../../feature-toggle/feature-toggle.directive';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle, NgbPopover } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from '../../language/translate.directive';
import { NgClass } from '@angular/common';
import { CdkCopyToClipboard } from '@angular/cdk/clipboard';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { RouterLink } from '@angular/router';
import { HelpIconComponent } from '../help-icon.component';
import { ArtemisTranslatePipe } from '../../pipes/artemis-translate.pipe';
import { SafeUrlPipe } from 'app/shared/pipes/safe-url.pipe';

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
        NgClass,
        CdkCopyToClipboard,
        FaIconComponent,
        RouterLink,
        HelpIconComponent,
        ArtemisTranslatePipe,
        SafeUrlPipe,
    ],
})
export class CodeButtonComponent implements OnInit, OnChanges {
    private translateService = inject(TranslateService);
    private externalCloningService = inject(ExternalCloningService);
    private sshUserSettingsService = inject(SshUserSettingsService);
    private accountService = inject(AccountService);
    private profileService = inject(ProfileService);
    private localStorage = inject(LocalStorageService);
    private participationService = inject(ParticipationService);
    private ideSettingsService = inject(IdeSettingsService);
    private programmingExerciseService = inject(ProgrammingExerciseService);

    readonly FeatureToggle = FeatureToggle;
    readonly ProgrammingLanguage = ProgrammingLanguage;

    @Input() loading = false;
    @Input() useParticipationVcsAccessToken = false;
    @Input() smallButtons: boolean;
    @Input() repositoryUri?: string;
    @Input() routerLinkForRepositoryView?: string | (string | number)[];
    @Input() participations?: ProgrammingExerciseStudentParticipation[];
    @Input() exercise?: ProgrammingExercise;
    @Input() hideLabelMobile = false;

    useSsh = false;
    useToken = false;
    tokenExpired = false;
    tokenMissing = false;
    sshEnabled = false;
    sshTemplateUrl?: string;
    sshSettingsUrl?: string;
    vcsTokenSettingsUrl?: string;
    repositoryPassword?: string;
    versionControlUrl: string;
    accessTokensEnabled?: boolean;
    localVCEnabled = false;
    gitlabVCEnabled = false;
    showCloneUrlWithoutToken = true;
    copyEnabled? = true;
    doesUserHaveSSHkeys = false;
    areAnySshKeysExpired = false;

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
    readonly faDesktop = faDesktop;
    ideName: string;

    async ngOnInit() {
        const user = await this.accountService.identity();
        if (!user) {
            return;
        }
        this.user = user;

        await this.checkForSshKeys();
        this.refreshTokenState();

        this.copyEnabled = true;
        this.useSsh = this.localStorage.retrieve('useSsh') || false;
        this.useToken = this.localStorage.retrieve('useToken') || false;

        // Get ssh information from the user
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.sshSettingsUrl = profileInfo.sshKeysURL;
            this.sshTemplateUrl = profileInfo.sshCloneURLTemplate;

            this.sshEnabled = !!this.sshTemplateUrl;
            if (profileInfo.versionControlUrl) {
                this.versionControlUrl = profileInfo.versionControlUrl;
            }
            this.accessTokensEnabled = profileInfo.useVersionControlAccessToken ?? false;
            this.showCloneUrlWithoutToken = profileInfo.showCloneUrlWithoutToken ?? true;
            this.useToken = !this.showCloneUrlWithoutToken;
            this.localVCEnabled = profileInfo.activeProfiles.includes(PROFILE_LOCALVC);
            this.gitlabVCEnabled = profileInfo.activeProfiles.includes(PROFILE_GITLAB);
            if (this.localVCEnabled) {
                this.sshSettingsUrl = `${window.location.origin}/user-settings/ssh`;
                this.vcsTokenSettingsUrl = `${window.location.origin}/user-settings/vcs-token`;
                this.tokenMissingTip = this.formatTip('artemisApp.exerciseActions.vcsTokenTip', this.vcsTokenSettingsUrl);
                this.tokenExpiredTip = this.formatTip('artemisApp.exerciseActions.vcsTokenExpiredTip', this.vcsTokenSettingsUrl);
            } else {
                this.sshSettingsUrl = profileInfo.sshKeysURL;
            }
            this.sshKeyMissingTip = this.formatTip('artemisApp.exerciseActions.sshKeyTip', this.sshSettingsUrl);
            this.sshKeysExpiredTip = this.formatTip('artemisApp.exerciseActions.sshKeyExpiredTip', this.sshSettingsUrl);

            if (this.useSsh) {
                this.useSshUrl();
            }
            if (this.useToken) {
                this.useHttpsUrlWithToken();
            }

            this.initTheia(profileInfo);
            this.loadParticipationVcsAccessTokens();
        });

        this.ideSettingsService.loadIdePreferences().then((programmingLanguageToIde) => {
            if (programmingLanguageToIde.size) {
                this.programmingLanguageToIde = programmingLanguageToIde;
            }

            this.ideName = this.getIde().name;
        });
    }

    ngOnChanges() {
        if (this.participations?.length) {
            const shouldPreferPractice = this.participationService.shouldPreferPractice(this.exercise);
            this.activeParticipation = this.participationService.getSpecificStudentParticipation(this.participations, shouldPreferPractice) ?? this.participations[0];
            this.isPracticeMode = isPracticeMode(this.activeParticipation);
            this.cloneHeadline =
                this.isPracticeMode && !this.exercise?.exerciseGroup ? 'artemisApp.exerciseActions.clonePracticeRepository' : 'artemisApp.exerciseActions.cloneRatedRepository';
            this.isTeamParticipation = !!this.activeParticipation?.team;
        } else if (this.repositoryUri) {
            this.cloneHeadline = 'artemisApp.exerciseActions.cloneExerciseRepository';
        }
        this.loadParticipationVcsAccessTokens();
    }

    public useSshUrl() {
        this.useSsh = true;
        this.useToken = false;
        this.copyEnabled = this.doesUserHaveSSHkeys || this.gitlabVCEnabled;
        this.storeToLocalStorage();
    }

    public useHttpsUrlWithToken() {
        this.useSsh = false;
        this.useToken = true;
        this.copyEnabled = !!(this.accessTokensEnabled && ((!!this.user.vcsAccessToken && !this.isTokenExpired()) || this.useParticipationVcsAccessToken));
        this.refreshTokenState();
        this.storeToLocalStorage();
    }

    public useHttpsUrlWithoutToken() {
        this.useSsh = false;
        this.useToken = false;
        this.copyEnabled = true;
        this.storeToLocalStorage();
    }

    public storeToLocalStorage() {
        this.localStorage.store('useSsh', this.useSsh);
        this.localStorage.store('useToken', this.useToken);
    }

    public refreshTokenState() {
        this.tokenMissing = !this.user.vcsAccessToken;
        this.tokenExpired = this.isTokenExpired();
    }

    public formatTip(translationKey: string, url: string): string {
        return this.translateService.instant(translationKey).replace(/{link:(.*)}/, `<a href="${url}" target="_blank">$1</a>`);
    }

    public isTokenExpired(): boolean {
        return dayjs().isAfter(dayjs(this.user.vcsAccessTokenExpiryDate));
    }

    private getRepositoryUri() {
        return this.activeParticipation?.repositoryUri ?? this.repositoryUri!;
    }

    getHttpOrSshRepositoryUri(insertPlaceholder = true): string {
        if (this.useSsh && this.sshEnabled && this.sshTemplateUrl) {
            return this.getSshCloneUrl(this.getRepositoryUri()) || this.getRepositoryUri();
        }
        if (this.isTeamParticipation) {
            return this.addCredentialsToHttpUrl(this.repositoryUriForTeam(this.getRepositoryUri()), insertPlaceholder);
        }
        return this.addCredentialsToHttpUrl(this.getRepositoryUri(), insertPlaceholder);
    }

    loadParticipationVcsAccessTokens() {
        if (this.accessTokensEnabled && this.localVCEnabled && this.useParticipationVcsAccessToken) {
            this.participations?.forEach((participation) => {
                if (participation?.id && !participation.vcsAccessToken) {
                    this.loadVcsAccessToken(participation);
                }
            });
            if (this.activeParticipation?.vcsAccessToken) {
                this.user.vcsAccessToken = this.activeParticipation?.vcsAccessToken;
            }
        }
    }

    /**
     * Loads the vcsAccessToken for a participation from the server. If none exists, sens a request to create one
     */
    loadVcsAccessToken(participation: ProgrammingExerciseStudentParticipation) {
        this.accountService.getVcsAccessToken(participation!.id!).subscribe({
            next: (res: HttpResponse<string>) => {
                if (res.body) {
                    participation.vcsAccessToken = res.body;
                    if (this.activeParticipation?.id == participation.id) {
                        this.user.vcsAccessToken = res.body;
                    }
                }
            },
            error: (error: HttpErrorResponse) => {
                if (error.status == 404) {
                    this.createNewVcsAccessToken(participation);
                }
                if (error.status == 403) {
                    this.useParticipationVcsAccessToken = false;
                }
            },
        });
    }

    /**
     * Sends the request to create a new
     */
    createNewVcsAccessToken(participation: ProgrammingExerciseStudentParticipation) {
        this.accountService.createVcsAccessToken(participation!.id!).subscribe({
            next: (res: HttpResponse<string>) => {
                if (res.body) {
                    participation.vcsAccessToken = res.body;
                    if (this.activeParticipation?.id == participation.id) {
                        this.user.vcsAccessToken = res.body;
                    }
                }
            },
            error: (error: HttpErrorResponse) => {
                if (error.status == 403) {
                    this.useParticipationVcsAccessToken = false;
                }
            },
        });
    }

    /**
     * Add the credentials to the http url, if possible.
     * The token will be added if
     * - the token is required (based on the profile information), and
     * - the token is present (based on the user model).
     *
     * @param url the url to which the credentials should be added
     * @param insertPlaceholder if true, instead of the actual token, '**********' is used (e.g. to prevent leaking the token during a screen-share)
     * @param alwaysToken if true, the token is always added, even if it is not required
     */
    private addCredentialsToHttpUrl(url: string, insertPlaceholder = false, alwaysToken = false): string {
        const includeToken = this.accessTokensEnabled && this.user.vcsAccessToken && (this.useToken || alwaysToken);
        const token = insertPlaceholder ? '**********' : this.user.vcsAccessToken;
        const credentials = `://${this.user.login}${includeToken ? `:${token}` : ''}@`;
        if (!url.includes('@')) {
            // the url has the format https://vcs-server.com
            return url.replace('://', credentials);
        } else {
            // the url has the format https://username@vcs-server.com -> replace ://username@
            return url.replace(/:\/\/.*@/, credentials);
        }
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
    private getSshCloneUrl(url?: string) {
        return url?.replace(/^\w*:\/\/[^/]*?\/(scm\/)?(.*)$/, this.sshTemplateUrl + '$2');
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
            this.programmingLanguageToIde.get(this.exercise?.programmingLanguage ?? ProgrammingLanguage.EMPTY) ??
            this.programmingLanguageToIde.get(ProgrammingLanguage.EMPTY) ??
            this.vscodeFallback
        );
    }

    switchPracticeMode() {
        this.isPracticeMode = !this.isPracticeMode;
        this.activeParticipation = this.participationService.getSpecificStudentParticipation(this.participations!, this.isPracticeMode)!;
        this.cloneHeadline = this.isPracticeMode ? 'artemisApp.exerciseActions.clonePracticeRepository' : 'artemisApp.exerciseActions.cloneRatedRepository';
        if (this.activeParticipation.vcsAccessToken) {
            this.user.vcsAccessToken = this.activeParticipation.vcsAccessToken;
        }
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

    private initTheia(profileInfo: ProfileInfo) {
        if (profileInfo.activeProfiles?.includes(PROFILE_THEIA) && this.exercise) {
            // Theia requires the Build Config of the programming exercise to be set
            this.programmingExerciseService.getBuildConfig(this.exercise.id!).subscribe((buildConfig) => {
                if (this.exercise) {
                    this.exercise.buildConfig = buildConfig;
                    // Set variables now, sanitize later on
                    this.theiaPortalURL = profileInfo.theiaPortalURL ?? '';
                    // Verify that all conditions are met
                    if (this.theiaPortalURL !== '' && this.exercise.allowOnlineIde && this.exercise.buildConfig?.theiaImage) {
                        this.theiaEnabled = true;
                    }
                }
            });
        }
    }

    async startOnlineIDE() {
        const artemisToken: string = (await this.accountService.getToolToken('SCORPIO').toPromise()) ?? '';

        let artemisUrl: string = '';
        if (window.location.protocol) {
            artemisUrl += window.location.protocol + '//';
        }
        if (window.location.host) {
            artemisUrl += window.location.host;
        }

        const data = {
            appDef: this.exercise?.buildConfig?.theiaImage ?? '',
            gitUri: this.addCredentialsToHttpUrl(this.getRepositoryUri(), false, true),
            gitUser: this.user.name,
            gitMail: this.user.email,
            artemisToken: artemisToken,
            artemisUrl: artemisUrl,
        };

        const newWindow = window.open('', '_blank');
        if (!newWindow) {
            return;
        }
        newWindow.name = 'Theia-IDE';

        const form = document.createElement('form');
        form.method = 'GET';
        form.action = this.theiaPortalURL;
        form.target = newWindow.name;

        // Loop over data element and create input fields
        for (const key in data) {
            if (Object.hasOwn(data, key)) {
                const hiddenField = document.createElement('input');
                hiddenField.type = 'hidden';
                hiddenField.name = key;
                const descriptor = Object.getOwnPropertyDescriptor(data, key);
                hiddenField.value = descriptor ? descriptor.value : '';
                form.appendChild(hiddenField);
            }
        }

        document.body.appendChild(form);
        form.submit();
        document.body.removeChild(form);
    }
}
