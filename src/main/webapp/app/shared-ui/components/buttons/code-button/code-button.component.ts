import { Component, OnInit, computed, effect, inject, input, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { BreakpointObserver } from '@angular/cdk/layout';
import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { FeatureToggle, FeatureToggleService } from 'app/foundation/feature-toggle/feature-toggle.service';
import { ExternalCloningService } from 'app/programming/shared/services/external-cloning.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { User } from 'app/account/user/user.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { MODULE_FEATURE_THEIA } from 'app/app.constants';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import dayjs from 'dayjs/esm';

import { faCircleNotch, faCode, faExternalLink, faLaptopCode } from '@fortawesome/free-solid-svg-icons';
import { UserSshPublicKey } from 'app/programming/shared/entities/user-ssh-public-key.model';
import { NgbDropdown, NgbDropdownMenu, NgbDropdownToggle } from '@ng-bootstrap/ng-bootstrap';
import { CdkCopyToClipboard } from '@angular/cdk/clipboard';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Router, RouterLink } from '@angular/router';
import { HelpIconComponent } from '../../help-icon/help-icon.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { SafeUrlPipe } from 'app/foundation/pipes/safe-url.pipe';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { AlertService } from 'app/foundation/service/alert.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { TheiaService } from 'app/programming/shared/services/theia.service';
import { SshUserSettingsService } from 'app/account/user/settings/ssh-settings/ssh-user-settings.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { IdeSettingsService } from 'app/account/user/settings/ide-preferences/ide-settings.service';
import { Ide } from 'app/account/user/settings/ide-preferences/ide.model';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { captureException } from '@sentry/angular';
import { cloneWith } from 'app/foundation/util/deep-clone.util';
import { ProgrammingExerciseBuildConfig } from 'app/programming/shared/entities/programming-exercise-build.config';
import { TumAetUiButtonDirective, TumAetUiPopoverComponent, TumAetUiPopoverTriggerDirective } from '@tumaet/ui-angular';
import { map } from 'rxjs/operators';
import { getSignalBasedOnRoute } from 'app/foundation/route/getSignalBasedOnRoute';

const BREAKPOINTS = {
    md: '(min-width: 768px)',
    xl: '(min-width: 1200px)',
} as const;

export enum RepositoryAuthenticationMethod {
    Password = 'password',
    Token = 'token',
    SSH = 'ssh',
}

export interface ProgrammingExerciseIdeConfiguration {
    programmingLanguage?: ProgrammingLanguage;
    allowOnlineIde?: boolean;
    buildConfig?: ProgrammingExerciseBuildConfig;
}

@Component({
    selector: 'jhi-code-button',
    templateUrl: './code-button.component.html',
    styleUrls: ['./code-button.component.scss'],
    imports: [
        TumAetUiButtonDirective,
        TumAetUiPopoverComponent,
        TumAetUiPopoverTriggerDirective,
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
    private ideSettingsService = inject(IdeSettingsService);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private alertService = inject(AlertService);
    private theiaService = inject(TheiaService);
    private router = inject(Router);
    private breakpointObserver = inject(BreakpointObserver);
    private featureToggleService = inject(FeatureToggleService);

    protected readonly ProgrammingLanguage = ProgrammingLanguage;
    protected readonly RepositoryAuthenticationMethod = RepositoryAuthenticationMethod;

    // Icons
    readonly faCircleNotch = faCircleNotch;
    readonly faCode = faCode;
    readonly faExternalLink = faExternalLink;
    readonly faLaptopCode = faLaptopCode;

    // Fields (immutable after construction)
    sshEnabled = false;
    sshTemplateUrl?: string;
    versionControlUrl!: string; // set in ngOnInit() from profile info
    sshSettingsUrl!: string; // set in configureTooltips() from ngOnInit()
    // Guards against reporting the same missing-token anomaly repeatedly (getHttpOrSshRepositoryUri runs on every change detection).
    private vcsAccessTokenReportedMissing = false;
    sshKeys?: UserSshPublicKey[];
    vscodeFallback: Ide = { name: 'VS Code', deepLink: 'vscode://vscode.git/clone?url={cloneUrl}' };
    programmingLanguageToIde: Map<ProgrammingLanguage, Ide> = new Map([[ProgrammingLanguage.EMPTY, this.vscodeFallback]]);
    theiaPortalURL!: string; // set in initTheia() from ngOnInit()

    // Input
    loading = input<boolean>(false);
    smallButtons = input.required<boolean>();
    repositoryUri = input.required<string>();
    routerLinkForRepositoryView = input.required<(string | number)[]>();
    // The student participation id, set by staff tables (scores, participations, feedback) that render the code button for an individual student repository without passing the
    // full participation object. It lets the repository-scoped staff token be minted for exactly that student repository.
    participationId = input<number>();
    participation = input<ProgrammingExerciseStudentParticipation>();
    programmingExerciseIdeConfiguration = input<ProgrammingExerciseIdeConfiguration>();
    hideLabelMobile = input<boolean>(false);
    hideLabelBreakpoint = input<'md' | 'xl'>('md');
    // When set to a base repository type (TEMPLATE, SOLUTION, TESTS, AUXILIARY), the code button uses a repository-scoped staff VCS access token instead of a participation token.
    repositoryType = input<RepositoryType>();
    auxiliaryRepositoryId = input<number>();
    // The exercise id used to load repository-scoped staff tokens.
    exerciseId = input.required<number>();
    readonly isInCourseManagement = getSignalBasedOnRoute(this.router, (url) => url.includes('course-management'));
    readonly user = signal<User | undefined>(undefined);
    // Set once a participation VCS token request has terminally failed to yield a token (empty response or an error that
    // is not the 404 → create fallback). Gates the missing-token Sentry report so it never fires while a token is still
    // in flight (an expected transient state during which the clone URL simply omits the token).
    private readonly participationTokenLoadFailed = signal(false);
    // Signals (we ideally declare everything related to change detection/UI to signals and leave component fields
    // as they are
    wasCopied = signal(false);
    copyEnabled = signal(false);
    doesUserHaveSSHkeys = signal(false);
    areAnySshKeysExpired = signal(false);
    // The repository-scoped VCS access token for course staff (used for base repositories and student repositories browsed by staff).
    repositoryAccessToken = signal<string | undefined>(undefined); // set exclusively through onClick and its called methods
    // The repository identity the cached repositoryAccessToken was minted for. The repository view reuses one code-button instance across base repositories (only the route
    // params change), so a token cached for the previous repository must not be reused for the next one: repository tokens are scoped to one exact repository URI and would fail
    // authentication. We therefore remember which repository the cached token belongs to and reload when it no longer matches the current repository.
    private repositoryAccessTokenIdentity?: string;
    sshKeyMissingTip = signal('');
    sshKeysExpiredTip = signal('');
    theiaEnabled = signal(false);
    ideName = signal('');
    // this is the fallback with a default order in case the server does not specify this as part of the profile info endpoint
    authenticationMechanisms = signal<RepositoryAuthenticationMethod[]>([
        RepositoryAuthenticationMethod.Token,
        RepositoryAuthenticationMethod.SSH,
        RepositoryAuthenticationMethod.Password,
    ]);
    private readonly matchesMediumBreakpoint = this.getMatchesBreakpointSignal(BREAKPOINTS.md);
    private readonly matchesExtraLargeBreakpoint = this.getMatchesBreakpointSignal(BREAKPOINTS.xl);
    readonly programmingExercisesEnabled = toSignal(this.featureToggleService.getFeatureToggleActive(FeatureToggle.ProgrammingExercises), { initialValue: true });
    readonly displayLabel = computed(() => this.computeDisplayLabel());
    selectedAuthenticationMechanism = signal<RepositoryAuthenticationMethod>(RepositoryAuthenticationMethod.Token);
    useToken = computed(() => this.selectedAuthenticationMechanism() === RepositoryAuthenticationMethod.Token);
    useSsh = computed(() => this.selectedAuthenticationMechanism() === RepositoryAuthenticationMethod.SSH);
    usePassword = computed(() => this.selectedAuthenticationMechanism() === RepositoryAuthenticationMethod.Password);
    isBaseRepository = computed(() => {
        const type = this.repositoryType();
        return type === RepositoryType.TEMPLATE || type === RepositoryType.SOLUTION || type === RepositoryType.TESTS || type === RepositoryType.AUXILIARY;
    });
    // True whenever the clone URL authenticates with a repository-scoped staff token that is minted on demand: for base
    // repositories (template/solution/tests/auxiliary) and for a student repository browsed by staff. Such tokens are
    // exact-URI scoped and provisioned automatically, so the personal VCS access token is never needed for cloning.
    usesRepositoryScopedToken = computed(() => this.shouldUseRepositoryScopedToken());

    constructor() {
        // we only loadVcsAccessToken if participations exist => reduces potentially repeated HTTP calls
        effect(() => {
            if (this.isBaseRepository()) {
                return;
            }
            const participation = this.participation();
            if (!participation) {
                return;
            }
            this.loadParticipationVcsAccessTokenIfMissing(participation);
        });
    }

    private shouldUseRepositoryScopedToken(): boolean {
        const usesStudentRepositoryStaffToken =
            this.isInCourseManagement() && (this.participationId() !== undefined || (!!this.participation() && !this.isOwnParticipation(this.participation())));
        const isBaseRepository = this.isBaseRepository();
        return isBaseRepository || usesStudentRepositoryStaffToken;
    }

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

    ngOnInit() {
        void this.initializeCodeButtonComponent();
    }

    private async initializeCodeButtonComponent(): Promise<void> {
        // Populate the tooltip strings first. They only depend on window.location.origin and the loaded
        // translations, not on the awaits below. The clone popover renders (and can be opened by the user)
        // before ngOnInit's async work finishes; if the SSH-key-missing alert appears while these strings
        // are still empty, the alert element has no text. Setting them up front guarantees the alert always
        // renders its message as soon as it becomes visible.
        this.configureTooltips();

        const user = await this.accountService.identity();
        if (!user) {
            return;
        }
        this.user.set(user);

        await this.checkForSshKeys();

        // Get ssh information from the user
        const profileInfo = this.profileService.getProfileInfo();
        this.sshTemplateUrl = profileInfo.sshCloneURLTemplate;

        if (profileInfo.repositoryAuthenticationMechanisms?.length) {
            const filteredMechanisms = profileInfo.repositoryAuthenticationMechanisms.filter((method): method is RepositoryAuthenticationMethod =>
                Object.values(RepositoryAuthenticationMethod).includes(method as RepositoryAuthenticationMethod),
            );
            if (filteredMechanisms.length) {
                this.authenticationMechanisms.set(filteredMechanisms);
            }
        }
        if (profileInfo.versionControlUrl) {
            this.versionControlUrl = profileInfo.versionControlUrl;
        }

        this.initTheia(profileInfo);

        void this.ideSettingsService.loadIdePreferences().then((programmingLanguageToIde) => {
            if (programmingLanguageToIde.size) {
                this.programmingLanguageToIde = programmingLanguageToIde;
            }
            this.ideName.set(this.getIde().name);
        });
    }

    public useSshUrl() {
        this.selectedAuthenticationMechanism.set(RepositoryAuthenticationMethod.SSH);
        this.copyEnabled.set(this.doesUserHaveSSHkeys());
        this.localStorageService.store<RepositoryAuthenticationMethod>('code-button-state', this.selectedAuthenticationMechanism());
    }

    public useHttpsToken() {
        this.selectedAuthenticationMechanism.set(RepositoryAuthenticationMethod.Token);
        if (this.usesRepositoryScopedToken()) {
            // The repository-scoped staff token is minted on demand; copy is enabled as soon as it has arrived for the current repository.
            this.copyEnabled.set(this.hasValidRepositoryAccessToken());
        } else {
            this.copyEnabled.set(!!this.participation()?.vcsAccessToken);
        }
        this.localStorageService.store<RepositoryAuthenticationMethod>('code-button-state', this.selectedAuthenticationMechanism());
    }

    public useHttpsPassword() {
        this.selectedAuthenticationMechanism.set(RepositoryAuthenticationMethod.Password);
        this.copyEnabled.set(true);
        this.localStorageService.store<RepositoryAuthenticationMethod>('code-button-state', this.selectedAuthenticationMechanism());
    }
    public formatTip(translationKey: string, url: string): string {
        return this.translateService.instant(translationKey).replace(/{link:(.*)}/, `<a href="${url}" target="_blank">$1</a>`);
    }

    onClick() {
        let storedState = this.localStorageService.retrieve<RepositoryAuthenticationMethod>('code-button-state');
        if (storedState === RepositoryAuthenticationMethod.Password) {
            storedState = RepositoryAuthenticationMethod.Token;
        }
        const selectedMechanism = storedState && this.authenticationMechanisms().includes(storedState) ? storedState : this.authenticationMechanisms()[0];
        this.selectedAuthenticationMechanism.set(selectedMechanism);

        // Fallback for course staff: generate the repository-scoped token on demand when the clone dialog is opened and none valid for the current repository exists yet. A token
        // cached for a previously viewed repository (this component instance is reused across repositories) is dropped first, so it can never be embedded into another repository's
        // clone URL. This covers both base repositories and student repositories browsed by staff, so the copy/clone button is never left disabled for a missing token.
        if (this.usesRepositoryScopedToken() && !this.hasValidRepositoryAccessToken()) {
            this.repositoryAccessToken.set(undefined);
            this.loadRepositoryScopedTokenForCurrentRepository();
        }

        if (this.useSsh()) {
            this.useSshUrl();
        }
        if (this.useToken()) {
            this.useHttpsToken();
        }
        if (this.usePassword()) {
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
        if (!alwaysReturnHttp && this.useSsh() && this.sshTemplateUrl) {
            return this.getSshCloneUrl(this.repositoryUri());
        }
        const url = this.repositoryUri();
        const useToken = this.useToken() || alwaysUsetoken;
        const token = insertPlaceholder ? '**********' : this.getUsedToken(alwaysUsetoken);

        // Never interpolate a missing token into the clone URL: an undefined/empty token would produce a broken
        // "://<login>:undefined@..." URL. If a token was expected but is unavailable, omit it entirely and report it.
        if (useToken && !token) {
            this.reportMissingVcsAccessToken();
        }
        const credentials = `://${this.user()!.login}${useToken && token ? `:${token}` : ''}@`;

        if (!url.includes('@')) {
            // the url has the format https://vcs-server.com
            return url.replace('://', credentials);
        } else {
            // the url has the format https://username@vcs-server.com -> replace ://username@
            return url.replace(/:\/\/.*@/, credentials);
        }
    }

    /**
     * Reports (once per component instance) that a clone URL had to be built without a VCS access token although one was
     * expected, so the anomaly stays observable while the URL itself never leaks a literal "undefined". Repository-scoped
     * staff tokens (base and student repositories) are intentionally excluded: they are minted on demand and have their
     * own dedicated error handling, so a transiently missing scoped token is not an anomaly worth reporting.
     */
    private reportMissingVcsAccessToken(): void {
        // Only report once a participation token request has terminally failed; never while the token is still loading
        // (the URL correctly omits the token during that transient window, so passive render must not raise an alarm).
        if (this.vcsAccessTokenReportedMissing || this.usesRepositoryScopedToken() || !this.participationTokenLoadFailed()) {
            return;
        }
        this.vcsAccessTokenReportedMissing = true;
        const participation = this.participation();
        captureException(
            new Error(
                `A VCS access token was expected but missing while building the clone URL (participationId=${participation?.id}, isInCourseManagement=${this.isInCourseManagement()}); the token was omitted from the URL.`,
            ),
        );
    }

    private loadParticipationVcsAccessTokenIfMissing(participation: ProgrammingExerciseStudentParticipation) {
        // Load the participation-scoped token only when the clone URL will actually use it: outside course management,
        // or for the user's own participation inside course management (e.g. an exam test run the instructor conducts).
        // For other participants' repositories in course management the personal staff token is used, and requesting
        // their participation token would be forbidden server-side.
        if ((!this.isInCourseManagement() || this.isOwnParticipation(participation)) && participation.id && !participation.vcsAccessToken) {
            this.loadParticipationVcsAccessToken(participation);
        }
    }

    /**
     * Whether the given participation belongs to the currently logged-in user. Used to decide whether the participation's
     * own VCS access token may be used (and loaded) even inside course management — e.g. an exam test run the instructor
     * conducts for themselves. For participations of other users (a student's, another instructor's test run) this is false,
     * so the personal staff token is used instead. Relies on the participation's `student` being populated, which is the
     * case for the exam test run summary/conduction flows where this distinction matters.
     */
    private isOwnParticipation(participation: ProgrammingExerciseStudentParticipation | undefined): boolean {
        const userLogin = this.user()?.login;
        return !!userLogin && participation?.student?.login === userLogin;
    }

    /**
     * Loads the vcsAccessToken for a participation from the server. If none exists, sends a request to create one
     * (Usually the token exists, as it is created when the server creates the participation)
     */
    loadParticipationVcsAccessToken(participation: ProgrammingExerciseStudentParticipation) {
        this.accountService.getVcsAccessToken(participation.id!).subscribe({
            next: (res: HttpResponse<string>) => {
                if (res.body) {
                    participation.vcsAccessToken = res.body;
                    // Only ever enable copy here; never disable. An async token response may arrive after the dialog
                    // opened with SSH/password selected, and must not clobber the copy state those methods already set.
                    if (this.useToken()) {
                        this.copyEnabled.set(true);
                    }
                } else {
                    // The server answered without a token: terminal failure, no create fallback follows.
                    this.participationTokenLoadFailed.set(true);
                }
            },
            error: (error: HttpErrorResponse) => {
                if (error.status == 404) {
                    this.createNewParticipationVcsAccessToken(participation);
                } else {
                    if (error.status == 403) {
                        this.alertService.warning('403 Forbidden');
                    }
                    this.participationTokenLoadFailed.set(true);
                }
            },
        });
    }

    /**
     * Sends the request to create a new participation VCS access token
     */
    createNewParticipationVcsAccessToken(participation: ProgrammingExerciseStudentParticipation) {
        this.accountService.createVcsAccessToken(participation.id!).subscribe({
            next: (res: HttpResponse<string>) => {
                if (res.body) {
                    participation.vcsAccessToken = res.body;
                    // Only ever enable copy here; never disable. An async token response may arrive after the dialog
                    // opened with SSH/password selected, and must not clobber the copy state those methods already set.
                    if (this.useToken()) {
                        this.copyEnabled.set(true);
                    }
                } else {
                    // Creating the token succeeded but returned no token: terminal failure.
                    this.participationTokenLoadFailed.set(true);
                }
            },
            error: (error: HttpErrorResponse) => {
                if (error.status == 403) {
                    this.alertService.warning('403 Forbidden');
                }
                this.participationTokenLoadFailed.set(true);
            },
        });
    }

    /**
     * Mints (or reuses) the repository-scoped staff token for the repository the clone dialog currently targets: a base repository (identified by its type) or a student assignment
     * repository (identified by its participation). Does nothing when the required identifiers (exercise id, and the participation id for a student repository) are unavailable.
     */
    private loadRepositoryScopedTokenForCurrentRepository() {
        const exerciseId = this.exerciseId();
        if (!exerciseId) {
            return;
        }
        if (this.isBaseRepository()) {
            this.loadRepositoryVcsAccessToken(exerciseId, this.repositoryType()!, this.auxiliaryRepositoryId());
            return;
        }
        const participationId = this.participationId() ?? this.participation()?.id;
        if (participationId) {
            this.loadRepositoryVcsAccessToken(exerciseId, RepositoryType.USER, undefined, participationId);
        }
    }

    /**
     * Loads the repository-scoped VCS access token for a repository (a base repository — template, tests, solution or auxiliary — or a student assignment repository identified by
     * its participation) of a programming exercise. If none exists yet, a new one is created (fallback when course staff open the clone dialog for the first time).
     */
    loadRepositoryVcsAccessToken(exerciseId: number, repositoryType: RepositoryType, auxiliaryRepositoryId?: number, participationId?: number) {
        const requestedRepositoryIdentity = this.currentRepositoryIdentity();
        this.programmingExerciseService.getRepositoryVcsAccessToken(exerciseId, repositoryType, auxiliaryRepositoryId, participationId).subscribe({
            next: (res: HttpResponse<string>) => {
                if (res.body && this.isCurrentRepositoryIdentity(requestedRepositoryIdentity)) {
                    this.setRepositoryAccessToken(res.body, requestedRepositoryIdentity);
                    // Only ever enable copy here; never disable. An async token response may arrive after the dialog
                    // opened with SSH/password selected, and must not clobber the copy state those methods already set.
                    if (this.useToken()) {
                        this.copyEnabled.set(true);
                    }
                }
            },
            error: (error: HttpErrorResponse) => {
                if (!this.isCurrentRepositoryIdentity(requestedRepositoryIdentity)) {
                    return;
                }
                if (error.status === 404) {
                    this.createRepositoryVcsAccessToken(exerciseId, repositoryType, auxiliaryRepositoryId, participationId, requestedRepositoryIdentity);
                } else if (error.status === 403) {
                    this.alertService.warning('artemisApp.exerciseActions.repositoryAccessTokenForbidden');
                } else {
                    this.alertService.error('artemisApp.exerciseActions.repositoryAccessTokenError');
                }
            },
        });
    }

    /**
     * Sends the request to create a new repository-scoped VCS access token for a repository (base or student assignment repository).
     */
    createRepositoryVcsAccessToken(
        exerciseId: number,
        repositoryType: RepositoryType,
        auxiliaryRepositoryId?: number,
        participationId?: number,
        requestedRepositoryIdentity = this.currentRepositoryIdentity(),
    ) {
        this.programmingExerciseService.createRepositoryVcsAccessToken(exerciseId, repositoryType, auxiliaryRepositoryId, participationId).subscribe({
            next: (res: HttpResponse<string>) => {
                if (res.body && this.isCurrentRepositoryIdentity(requestedRepositoryIdentity)) {
                    this.setRepositoryAccessToken(res.body, requestedRepositoryIdentity);
                    // Only ever enable copy here; never disable. An async token response may arrive after the dialog
                    // opened with SSH/password selected, and must not clobber the copy state those methods already set.
                    if (this.useToken()) {
                        this.copyEnabled.set(true);
                    }
                }
            },
            error: (error: HttpErrorResponse) => {
                if (!this.isCurrentRepositoryIdentity(requestedRepositoryIdentity)) {
                    return;
                }
                if (error.status === 403) {
                    this.alertService.warning('artemisApp.exerciseActions.repositoryAccessTokenForbidden');
                } else {
                    this.alertService.error('artemisApp.exerciseActions.repositoryAccessTokenError');
                }
            },
        });
    }

    /**
     * Stores a newly retrieved repository-scoped token together with the identity of the repository it was minted for, so it is only ever reused for that exact repository.
     */
    private setRepositoryAccessToken(token: string, repositoryIdentity: string) {
        this.repositoryAccessToken.set(token);
        this.repositoryAccessTokenIdentity = repositoryIdentity;
    }

    /**
     * @return whether a cached repository-scoped token exists and still belongs to the base repository currently targeted by the clone dialog.
     */
    private hasValidRepositoryAccessToken(): boolean {
        return !!this.repositoryAccessToken() && this.isCurrentRepositoryIdentity(this.repositoryAccessTokenIdentity);
    }

    /**
     * @return whether the given repository identity still describes the repository currently targeted by this reused component instance.
     */
    private isCurrentRepositoryIdentity(repositoryIdentity: string | undefined): boolean {
        return !!repositoryIdentity && repositoryIdentity === this.currentRepositoryIdentity();
    }

    /**
     * A stable key identifying the repository the clone dialog currently targets (type, URI, exercise, optional auxiliary repository and optional student participation). Used to
     * detect when this reused component instance switches to a different repository, so a repository-scoped token minted for the previous repository is never reused.
     */
    private currentRepositoryIdentity(): string {
        return [this.repositoryType(), this.repositoryUri(), this.exerciseId(), this.auxiliaryRepositoryId(), this.participationId() ?? this.participation()?.id].join('|');
    }

    private getUsedToken(alwaysUseToken = false): string | undefined {
        if (this.useToken() || alwaysUseToken) {
            if (this.usesRepositoryScopedToken()) {
                // Never embed a token cached for a different repository (exact-URI scoped); only the token for the current repository is valid.
                return this.hasValidRepositoryAccessToken() ? this.repositoryAccessToken() : undefined;
            }
            return this.participation()?.vcsAccessToken;
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
            this.wasCopied.set(true);
            setTimeout(() => {
                this.wasCopied.set(false);
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
            this.programmingLanguageToIde.get(this.programmingExerciseIdeConfiguration()?.programmingLanguage ?? ProgrammingLanguage.EMPTY) ??
            this.programmingLanguageToIde.get(ProgrammingLanguage.EMPTY) ??
            this.vscodeFallback
        );
    }

    /**
     * Checks whether the user owns any SSH keys, and checks if any of them is expired
     */
    private async checkForSshKeys() {
        this.sshKeys = await this.sshUserSettingsService.getCachedSshKeys();
        if (this.sshKeys) {
            const now = dayjs();
            this.doesUserHaveSSHkeys.set(this.sshKeys.length > 0);
            const areSSHkeysExpired = this.sshKeys.some((key) => {
                if (key.expiryDate) {
                    return dayjs(key.expiryDate).isBefore(now);
                }
                return false;
            });
            this.areAnySshKeysExpired.set(areSSHkeysExpired);
        }
    }

    private configureTooltips() {
        this.sshSettingsUrl = `${window.location.origin}/user-settings/ssh`;
        this.sshKeyMissingTip.set(this.formatTip('artemisApp.exerciseActions.sshKeyTip', this.sshSettingsUrl));
        this.sshKeysExpiredTip.set(this.formatTip('artemisApp.exerciseActions.sshKeyExpiredTip', this.sshSettingsUrl));
    }

    private initTheia(profileInfo: ProfileInfo) {
        const programmingExerciseIdeConfiguration = this.programmingExerciseIdeConfiguration();
        const exerciseId = this.exerciseId();
        if (this.profileService.isModuleFeatureActive(MODULE_FEATURE_THEIA) && programmingExerciseIdeConfiguration && exerciseId) {
            // Theia requires the Build Config of the programming exercise to be set
            this.programmingExerciseService.getTheiaConfig(exerciseId).subscribe((theiaConfig) => {
                // Merge the theiaConfig (containing the theiaImage) into the buildConfig
                // The exercise may arrive without a build config; the previous spread tolerated that, so fall back to a
                // fresh one rather than cloning undefined.
                programmingExerciseIdeConfiguration.buildConfig = cloneWith(programmingExerciseIdeConfiguration.buildConfig ?? new ProgrammingExerciseBuildConfig(), theiaConfig);

                // Set variables now, sanitize later on
                this.theiaPortalURL = profileInfo.theiaPortalURL ?? '';

                // Verify that all conditions are met
                if (this.theiaPortalURL !== '' && programmingExerciseIdeConfiguration.allowOnlineIde && theiaConfig.theiaImage) {
                    this.theiaEnabled.set(true);
                }
            });
        }
    }

    async startOnlineIDE() {
        const theiaImage = this.programmingExerciseIdeConfiguration()?.buildConfig?.theiaImage ?? '';
        const repositoryUri = this.getHttpOrSshRepositoryUri(false, true, true);
        const userName = this.user()!.name;
        const userEmail = this.user()!.email;

        await this.theiaService.startOnlineIDE(this.theiaPortalURL, theiaImage, repositoryUri, userName, userEmail);
    }
}
