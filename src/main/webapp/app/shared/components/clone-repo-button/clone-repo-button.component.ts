import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { SourceTreeService } from 'app/exercises/programming/shared/service/sourceTree.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { LocalStorageService } from 'ngx-webstorage';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { Exercise } from 'app/entities/exercise.model';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { isPracticeMode } from 'app/entities/participation/student-participation.model';
import { faDownload, faExternalLink } from '@fortawesome/free-solid-svg-icons';
import { AlertService } from 'app/core/util/alert.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { from, zip } from 'rxjs';

@Component({
    selector: 'jhi-clone-repo-button',
    templateUrl: './clone-repo-button.component.html',
    styleUrls: ['./clone-repo-button.component.scss'],
})
export class CloneRepoButtonComponent implements OnInit, OnChanges {
    readonly FeatureToggle = FeatureToggle;

    @Input()
    loading = false;
    @Input()
    smallButtons: boolean;
    @Input()
    repositoryUri?: string;
    @Input()
    participations?: ProgrammingExerciseStudentParticipation[];
    @Input()
    exercise?: Exercise;

    useSsh = false;
    sshKeysUrl: string;
    sshEnabled: boolean;
    sshTemplateUrl: string;
    repositoryPassword: string;
    versionControlUrl: string;
    versionControlAccessTokenRequired?: boolean;
    localVCEnabled = false;
    user: User;
    cloneHeadline: string;
    wasCopied = false;
    isTeamParticipation: boolean;
    activeParticipation?: ProgrammingExerciseStudentParticipation;
    isPracticeMode: boolean | undefined;
    currentlyLoadingToken: boolean = false;

    // Icons
    faDownload = faDownload;
    faExternalLink = faExternalLink;

    constructor(
        private translateService: TranslateService,
        private sourceTreeService: SourceTreeService,
        private accountService: AccountService,
        private alertService: AlertService,
        private profileService: ProfileService,
        private localStorage: LocalStorageService,
        private participationService: ParticipationService,
    ) {}

    ngOnInit() {
        const userObservable = from(this.accountService.identity());
        const profileInfoObservable = this.profileService.getProfileInfo();
        const userProfileInfoZip = zip(userObservable, profileInfoObservable);
        userProfileInfoZip.subscribe((userAndProfileInfo) => {
            const user = userAndProfileInfo[0];
            const profileInfo = userAndProfileInfo[1];

            this.user = user!;
            this.setProfileInfo(profileInfo);
            if (this.versionControlAccessTokenRequired && !this.user.vcsAccessToken) {
                this.getVSCToken();
            }
        });

        this.useSsh = this.localStorage.retrieve('useSsh') || false;
        this.localStorage.observe('useSsh').subscribe((useSsh) => (this.useSsh = useSsh || false));
    }

    public setUseSSH(useSsh: boolean) {
        this.useSsh = useSsh;
        this.localStorage.store('useSsh', this.useSsh);
    }

    private setProfileInfo(profileInfo: ProfileInfo) {
        this.sshKeysUrl = profileInfo.sshKeysURL;
        this.sshTemplateUrl = profileInfo.sshCloneURLTemplate;
        this.sshEnabled = !!this.sshTemplateUrl;
        if (profileInfo.versionControlUrl) {
            this.versionControlUrl = profileInfo.versionControlUrl;
        }
        this.versionControlAccessTokenRequired = profileInfo.versionControlAccessToken;
        this.localVCEnabled = profileInfo.activeProfiles.includes(PROFILE_LOCALVC);
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
    }

    /**
     * Sometimes the VCS access token is not present in the user object, so the resulting repo url is wrong. This method
     * tries to retrieve the token from the server by requesting the user object again.
     * This case only happens when a user is created before VCS access tokens are enabled. For those users, a token is
     * created the first time they start participating in a programming exercise. In that case, however, they do not
     * have an access token yet, because during login, there was no token available.
     *
     * In case the token could not be retrieved, we prompt the student to reload the page.
     */
    getVSCToken() {
        this.currentlyLoadingToken = true;
        this.accountService
            .identity(true)
            .then((user) => {
                this.user = user ?? this.user;
                this.currentlyLoadingToken = false;
                if (!this.user.vcsAccessToken) {
                    this.alertService.error('artemisApp.exerciseActions.fetchVCSAccessTokenError');
                }
            })
            .catch(() => {
                this.alertService.error('artemisApp.exerciseActions.fetchVCSAccessTokenError');
            });
    }

    private getRepositoryUri() {
        return this.activeParticipation?.repositoryUri ?? this.repositoryUri!;
    }

    getHttpOrSshRepositoryUri(insertPlaceholder = true): string {
        if (this.useSsh) {
            return this.getSshCloneUrl(this.getRepositoryUri()) || this.getRepositoryUri();
        }

        if (this.isTeamParticipation) {
            return this.addCredentialsToHttpUrl(this.repositoryUriForTeam(this.getRepositoryUri()), insertPlaceholder);
        }

        return this.addCredentialsToHttpUrl(this.getRepositoryUri(), insertPlaceholder);
    }

    /**
     * Add the credentials to the http url, if possible.
     * The token will be added if
     * - the token is required (based on the profile information), and
     * - the token is present (based on the user model).
     *
     * @param url the url to which the credentials should be added
     * @param insertPlaceholder if true, instead of the actual token, '**********' is used (e.g. to prevent leaking the token during a screen-share)
     */
    private addCredentialsToHttpUrl(url: string, insertPlaceholder = false): string {
        const includeToken = this.versionControlAccessTokenRequired && this.user.vcsAccessToken;
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
     * Used for the Button to open the repository in a separate browser-window
     * @return HTTPS-Repository link of the student
     */
    getHttpRepositoryUri(): string {
        if (this.isTeamParticipation) {
            return this.repositoryUriForTeam(this.getRepositoryUri());
        } else {
            return this.getRepositoryUri();
        }
    }

    /**
     * The user info part of the repository uri of a team participation has to be added with the current user's login.
     *
     * @return repository uri with username of current user inserted
     */
    private repositoryUriForTeam(url: string) {
        // (https://)(bitbucket.ase.in.tum.de/...-team1.git)  =>  (https://)ga12abc@(bitbucket.ase.in.tum.de/...-team1.git)
        return url.replace(/^(\w*:\/\/)(.*)$/, `$1${this.user.login}@$2`);
    }

    /**
     * Transforms the repository uri to an ssh clone url
     */
    private getSshCloneUrl(url?: string) {
        return url?.replace(/^\w*:\/\/[^/]*?\/(scm\/)?(.*)$/, this.sshTemplateUrl + '$2');
    }

    /**
     * Inserts the correct link to the translated ssh tip.
     */
    getSshKeyTip() {
        return this.translateService.instant('artemisApp.exerciseActions.sshKeyTip').replace(/{link:(.*)}/, '<a href="' + this.sshKeysUrl + '" target="_blank">$1</a>');
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
    buildSourceTreeUrl() {
        return this.sourceTreeService.buildSourceTreeUrl(this.versionControlUrl, this.getHttpOrSshRepositoryUri(false));
    }

    switchPracticeMode() {
        this.isPracticeMode = !this.isPracticeMode;
        this.activeParticipation = this.participationService.getSpecificStudentParticipation(this.participations!, this.isPracticeMode)!;
        this.cloneHeadline = this.isPracticeMode ? 'artemisApp.exerciseActions.clonePracticeRepository' : 'artemisApp.exerciseActions.cloneRatedRepository';
    }
}
