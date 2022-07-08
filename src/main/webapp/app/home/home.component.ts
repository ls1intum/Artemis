import { AfterViewChecked, Component, ElementRef, OnInit, Renderer2 } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, Router } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { Credentials } from 'app/core/auth/auth-jwt.service';
import { HttpErrorResponse } from '@angular/common/http';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { isOrion } from 'app/shared/orion/orion';
import { ModalConfirmAutofocusComponent } from 'app/shared/orion/modal-confirm-autofocus/modal-confirm-autofocus.component';
import { AccountService } from 'app/core/auth/account.service';
import { LoginService } from 'app/core/login/login.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { StateStorageService } from 'app/core/auth/state-storage.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { PASSWORD_MIN_LENGTH, USERNAME_MIN_LENGTH } from 'app/app.constants';
import { EventManager } from 'app/core/util/event-manager.service';
import { AlertService } from 'app/core/util/alert.service';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-home',
    templateUrl: './home.component.html',
    styleUrls: ['home.scss'],
})
export class HomeComponent implements OnInit, AfterViewChecked {
    USERNAME_MIN_LENGTH = USERNAME_MIN_LENGTH;
    PASSWORD_MIN_LENGTH = PASSWORD_MIN_LENGTH;
    authenticationError = false;
    authenticationAttempts = 0;
    account: User;
    modalRef: NgbModalRef;
    password: string;
    rememberMe = true;
    // in case this is activated (see application-artemis.yml), users have to actively click into it
    needsToAcceptTerms = false;
    userAcceptedTerms = false;
    username: string;
    captchaRequired = false;
    credentials: Credentials;
    isRegistrationEnabled = false;
    isPasswordLoginDisabled = false;
    loading = true;
    mainElementFocused = false;

    // if the server is not connected to an external user management such as JIRA, we accept all valid username patterns
    usernameRegexPattern = /^[a-z0-9_-]{3,50}$/; // default, might be overridden in ngOnInit
    errorMessageUsername = 'home.errors.usernameIncorrect'; // default, might be overridden in ngOnInit
    accountName?: string; // additional information in the welcome message

    externalUserManagementActive = true;
    externalUserManagementUrl: string;
    externalUserManagementName: string;

    isSubmittingLogin = false;

    profileInfo: ProfileInfo | undefined = undefined;

    // Icons
    faCircleNotch = faCircleNotch;

    constructor(
        private router: Router,
        private activatedRoute: ActivatedRoute,
        private accountService: AccountService,
        private loginService: LoginService,
        private stateStorageService: StateStorageService,
        private elementRef: ElementRef,
        private renderer: Renderer2,
        private eventManager: EventManager,
        private guidedTourService: GuidedTourService,
        private orionConnectorService: OrionConnectorService,
        private modalService: NgbModal,
        private profileService: ProfileService,
        private alertService: AlertService,
    ) {}

    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (profileInfo) {
                this.initializeWithProfileInfo(profileInfo);
            }
        });
        this.accountService.identity().then((user) => {
            this.currentUserCallback(user!);

            // Once this has loaded and the user is not defined, we know we need the user to log in
            if (!user) {
                this.loading = false;
            }
        });
        this.registerAuthenticationSuccess();
    }

    /**
     * Initializes the component with the required information received from the server.
     * @param profileInfo The information from the server how logins should be handled.
     * @private
     */
    private initializeWithProfileInfo(profileInfo: ProfileInfo) {
        this.profileInfo = profileInfo;

        if (profileInfo.activeProfiles.includes('jira')) {
            this.externalUserManagementUrl = profileInfo.externalUserManagementURL;
            this.externalUserManagementName = profileInfo.externalUserManagementName;
            if (profileInfo.allowedLdapUsernamePattern) {
                this.usernameRegexPattern = new RegExp(profileInfo.allowedLdapUsernamePattern);
            }
        } else {
            // TODO: in the future we might also allow external user management for non jira profiles
            this.externalUserManagementActive = false;
        }

        this.accountName = profileInfo.accountName;
        if (this.accountName === 'TUM') {
            this.errorMessageUsername = 'home.errors.tumWarning';
        }

        this.isRegistrationEnabled = !!profileInfo.registrationEnabled;
        this.needsToAcceptTerms = !!profileInfo.needsToAcceptTerms;
        this.activatedRoute.queryParams.subscribe((params) => {
            const loginFormOverride = params.hasOwnProperty('showLoginForm');
            this.isPasswordLoginDisabled = !!this.profileInfo?.saml2 && this.profileInfo.saml2.passwordLoginDisabled && !loginFormOverride;
        });
    }

    registerAuthenticationSuccess() {
        const subscription = this.eventManager.subscribe('authenticationSuccess', () => {
            // We only need to authenticate once, make sure we don't run this subscription multiple times
            this.eventManager.destroy(subscription);

            this.accountService.identity().then((user) => {
                this.currentUserCallback(user!);
            });
        });
    }

    ngAfterViewChecked() {
        // Only focus the username input once, not on every update
        if (this.mainElementFocused || this.loading) {
            return;
        }

        // Focus on the main element as soon as it is visible
        const mainElement = this.renderer.selectRootElement(this.isPasswordLoginDisabled ? '#saml2Button' : '#username', true);
        if (mainElement) {
            mainElement.focus();
            this.mainElementFocused = true;
        }

        // If the session expired or similar display a warning
        if (this.loginService.lastLogoutWasForceful()) {
            this.alertService.error('home.errors.sessionExpired');
        }
    }

    login() {
        this.isSubmittingLogin = true;
        this.loginService
            .login({
                username: this.username,
                password: this.password,
                rememberMe: this.rememberMe,
            })
            .then(() => this.handleLoginSuccess())
            .catch((error: HttpErrorResponse) => {
                // TODO: if registration is enabled, handle the case "User was not activated"
                this.captchaRequired = error.headers.get('X-artemisApp-error') === 'CAPTCHA required';
                this.authenticationError = true;
                this.authenticationAttempts++;
            })
            .finally(() => (this.isSubmittingLogin = false));
    }

    /**
     * Handle a successful user login.
     * @private
     */
    private handleLoginSuccess() {
        this.authenticationError = false;
        this.authenticationAttempts = 0;
        this.captchaRequired = false;

        if (this.router.url === '/register' || /^\/activate\//.test(this.router.url) || /^\/reset\//.test(this.router.url)) {
            this.router.navigate(['']);
        }

        this.eventManager.broadcast({
            name: 'authenticationSuccess',
            content: 'Sending Authentication Success',
        });

        this.handleOrionLogin();
    }

    /**
     * Handle special login procedures when inside the Orion plugin.
     * @private
     */
    private handleOrionLogin() {
        if (!isOrion) {
            return;
        }

        const modalRef: NgbModalRef = this.modalService.open(ModalConfirmAutofocusComponent as Component, { size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.text = 'login.ide.confirmation';
        modalRef.componentInstance.title = 'login.ide.title';
        modalRef.result.then(
            () => this.orionConnectorService.login(this.username, this.password),
            () => {},
        );
    }

    currentUserCallback(account: User) {
        this.account = account;
        if (account) {
            // previousState was set in the authExpiredInterceptor before being redirected to login modal.
            // since login is successful, go to stored previousState and clear previousState
            const redirect = this.stateStorageService.getUrl();
            if (redirect && redirect !== '') {
                this.stateStorageService.storeUrl('');
                this.router.navigateByUrl(redirect);
            } else {
                // TODO: Remove redirect after summer 2021 term. New deep links should no longer use /#.
                const url = this.router.url.startsWith('/#') ? this.router.url.slice(2) : 'courses';
                this.router.navigate([url]);
            }
        }
    }

    isAuthenticated() {
        return this.accountService.isAuthenticated();
    }

    inputChange(event: any) {
        if (event.target && event.target.name === 'username') {
            this.username = event.target.value;
        }
        if (event.target && event.target.name === 'password') {
            this.password = event.target.value;
        }
    }
}
