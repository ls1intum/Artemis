import { Component, Input, OnInit } from '@angular/core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { SourceTreeService } from 'app/exercises/programming/shared/service/sourceTree.service';
import { TranslateService } from '@ngx-translate/core';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { LocalStorageService } from 'ngx-webstorage';
import { faDownload, faExternalLink } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';

@Component({
    selector: 'jhi-clone-repo-button',
    templateUrl: './clone-repo-button.component.html',
    styleUrls: ['./clone-repo-button.component.scss'],
})
export class CloneRepoButtonComponent implements OnInit {
    readonly FeatureToggle = FeatureToggle;

    @Input()
    loading = false;
    @Input()
    smallButtons: boolean;
    @Input()
    repositoryUrl?: string;
    @Input()
    participations?: ProgrammingExerciseStudentParticipation[];

    useSsh = false;
    sshKeysUrl: string;
    sshEnabled: boolean;
    sshTemplateUrl: string;
    repositoryPassword: string;
    versionControlUrl: string;
    versionControlAccessTokenRequired?: boolean;
    user: User;
    cloneHeadline: string;
    wasCopied = false;
    isTeamParticipation: boolean;
    activeParticipation?: ProgrammingExerciseStudentParticipation;

    // Icons
    faDownload = faDownload;
    faExternalLink = faExternalLink;

    constructor(
        private translateService: TranslateService,
        private sourceTreeService: SourceTreeService,
        private accountService: AccountService,
        private profileService: ProfileService,
        private localStorage: LocalStorageService,
        private participationService: ParticipationService,
    ) {}

    ngOnInit() {
        this.accountService.identity().then((user) => {
            this.user = user!;
        });

        // Get ssh information from the user
        this.profileService.getProfileInfo().subscribe((info: ProfileInfo) => {
            this.sshKeysUrl = info.sshKeysURL;
            this.sshTemplateUrl = info.sshCloneURLTemplate;
            this.sshEnabled = !!this.sshTemplateUrl;
            if (info.versionControlUrl) {
                this.versionControlUrl = info.versionControlUrl;
            }
            this.versionControlAccessTokenRequired = info.versionControlAccessToken;
        });

        this.useSsh = this.localStorage.retrieve('useSsh') || false;
        this.localStorage.observe('useSsh').subscribe((useSsh) => (this.useSsh = useSsh || false));

        if (this.participations?.length) {
            this.isTeamParticipation = !!this.participations.first()?.team;
            this.activeParticipation = this.participationService.getSpecificStudentParticipation(this.participations, true) ?? this.participations[0];
            this.cloneHeadline = this.activeParticipation.testRun ? 'artemisApp.exerciseActions.clonePracticeRepository' : 'artemisApp.exerciseActions.cloneRatedRepository';
        } else if (this.repositoryUrl) {
            this.cloneHeadline = 'artemisApp.exerciseActions.cloneExerciseRepository';
        }
    }

    public setUseSSH(useSsh: boolean) {
        this.useSsh = useSsh;
        this.localStorage.store('useSsh', this.useSsh);
    }

    private getRepositoryUrl() {
        return this.activeParticipation?.repositoryUrl ?? this.repositoryUrl!;
    }

    getHttpOrSshRepositoryUrl(insertPlaceholder = true): string {
        if (this.useSsh) {
            return this.getSshCloneUrl(this.getRepositoryUrl()) || this.getRepositoryUrl();
        }

        if (this.isTeamParticipation) {
            return this.addAccessTokenToHttpUrl(this.repositoryUrlForTeam(this.getRepositoryUrl()), insertPlaceholder);
        }

        return this.addAccessTokenToHttpUrl(this.getRepositoryUrl(), insertPlaceholder);
    }

    /**
     * Add the access token to the http url, if possible.
     * The token will be added if
     * - the token is required (based on the profile information), and
     * - the token is present (based on the user model).
     *
     * It will only be added if a username is present in the given url and will be added after the username and before the host name.
     * @param url the url to which the token should be added
     * @param insertPlaceholder if true, instead of the actual token, '**********' is used (e.g. to prevent leaking the token during a screen-share)
     */
    private addAccessTokenToHttpUrl(url: string, insertPlaceholder = false): string {
        const vcsAccessToken = this.user.vcsAccessToken;
        // If the token is not present or not required, don't include it
        if (!this.versionControlAccessTokenRequired || !vcsAccessToken || !url) {
            return url;
        }

        const token = insertPlaceholder ? '**********' : vcsAccessToken;
        const urlUserInfoPart = `://${this.user.login}:${token}@`;
        if (!url.includes('@')) {
            // the url has the format https://vcs-server.com
            return url.replace('://', urlUserInfoPart);
        } else {
            // the url has the format https://username@vcs-server.com -> replace ://username@
            return url.replace(/:\/\/.*@/, urlUserInfoPart);
        }
    }

    /**
     * Used for the Button to open the repository in a separate browser-window
     * @return HTTPS-Repository link of the student
     */
    getHttpRepositoryUrl(): string {
        if (this.isTeamParticipation) {
            return this.repositoryUrlForTeam(this.getRepositoryUrl());
        } else {
            return this.getRepositoryUrl();
        }
    }

    /**
     * The user info part of the repository url of a team participation has to be added with the current user's login.
     *
     * @return repository url with username of current user inserted
     */
    private repositoryUrlForTeam(url: string) {
        // (https://)(bitbucket.ase.in.tum.de/...-team1.git)  =>  (https://)ga12abc@(bitbucket.ase.in.tum.de/...-team1.git)
        return url.replace(/^(\w*:\/\/)(.*)$/, `$1${this.user.login}@$2`);
    }

    /**
     * Transforms the repository url to an ssh clone url
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
     * build the sourceTreeUrl from the repository url
     * @return sourceTreeUrl
     */
    buildSourceTreeUrl() {
        return this.sourceTreeService.buildSourceTreeUrl(this.versionControlUrl, this.getHttpOrSshRepositoryUrl(false));
    }

    switchPracticeMode() {
        this.activeParticipation = this.participationService.getSpecificStudentParticipation(this.participations!, !this.activeParticipation?.testRun)!;
        this.cloneHeadline = this.activeParticipation.testRun ? 'artemisApp.exerciseActions.clonePracticeRepository' : 'artemisApp.exerciseActions.cloneRatedRepository';
    }
}
