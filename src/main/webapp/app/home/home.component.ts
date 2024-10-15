import { AfterViewChecked, Component, OnInit, Renderer2, inject } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { Credentials } from 'app/core/auth/auth-jwt.service';
import { OrionConnectorService } from 'app/shared/orion/orion-connector.service';
import { isOrion } from 'app/shared/orion/orion';
import { ModalConfirmAutofocusComponent } from 'app/shared/orion/modal-confirm-autofocus/modal-confirm-autofocus.component';
import { AccountService } from 'app/core/auth/account.service';
import { LoginService } from 'app/core/login/login.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { StateStorageService } from 'app/core/auth/state-storage.service';
import { ProfileInfo } from 'app/shared/layouts/profiles/profile-info.model';
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH, USERNAME_MAX_LENGTH, USERNAME_MIN_LENGTH } from 'app/app.constants';
import { EventManager } from 'app/core/util/event-manager.service';
import { AlertService } from 'app/core/util/alert.service';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { TranslateDirective } from '../shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Saml2LoginComponent } from './saml2-login/saml2-login.component';

@Component({
    selector: 'jhi-home',
    templateUrl: './home.component.html',
    styleUrls: ['home.scss'],
    standalone: true,
    imports: [TranslateDirective, FormsModule, RouterLink, FaIconComponent, Saml2LoginComponent, ArtemisSharedModule],
})
export class HomeComponent implements OnInit, AfterViewChecked {
    private router = inject(Router);
    private activatedRoute = inject(ActivatedRoute);
    private accountService = inject(AccountService);
    private loginService = inject(LoginService);
    private stateStorageService = inject(StateStorageService);
    private renderer = inject(Renderer2);
    private eventManager = inject(EventManager);
    private orionConnectorService = inject(OrionConnectorService);
    private modalService = inject(NgbModal);
    private profileService = inject(ProfileService);
    private alertService = inject(AlertService);
    private translateService = inject(TranslateService);

    USERNAME_MIN_LENGTH = USERNAME_MIN_LENGTH;
    USERNAME_MAX_LENGTH = USERNAME_MAX_LENGTH;
    PASSWORD_MIN_LENGTH = PASSWORD_MIN_LENGTH;
    PASSWORD_MAX_LENGTH = PASSWORD_MAX_LENGTH;
    authenticationError = false;
    account: User;
    modalRef: NgbModalRef;
    password: string;
    rememberMe = true;
    // in case this is activated (see application-artemis.yml), users have to actively click into it
    needsToAcceptTerms = false;
    userAcceptedTerms = false;
    username: string;
    credentials: Credentials;
    isRegistrationEnabled = false;
    isPasswordLoginDisabled = false;
    loading = true;
    mainElementFocused = false;

    usernamePlaceholder = 'global.form.username.placeholder'; // default, might be overridden
    usernamePlaceholderTranslated = 'Login or email'; // default, might be overridden
    // if the server is not connected to an external user management, we accept all valid username patterns
    usernameRegexPattern = /^[a-zA-Z0-9.@_-]{4,50}$/; // default (at least 4, at most 50 characters), might be overridden
    errorMessageUsername = 'home.errors.usernameIncorrect'; // default, might be overridden
    accountName?: string; // additional information in the welcome message

    externalUserManagementActive = true;

    isFormValid = false;
    isSubmittingLogin = false;

    profileInfo: ProfileInfo | undefined = undefined;

    // Icons
    faCircleNotch = faCircleNotch;
    usernameTouched = false;
    passwordTouched = false;

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

        const prefilledUsername = this.accountService.getAndClearPrefilledUsername();
        if (prefilledUsername) {
            this.username = prefilledUsername;
        }
    }

    /**
     * Initializes the component with the required information received from the server.
     * @param profileInfo The information from the server how logins should be handled.
     */
    private initializeWithProfileInfo(profileInfo: ProfileInfo) {
        this.profileInfo = profileInfo;
        this.externalUserManagementActive = false;

        this.accountName = profileInfo.accountName;
        if (profileInfo.allowedLdapUsernamePattern) {
            this.usernameRegexPattern = new RegExp(profileInfo.allowedLdapUsernamePattern);
        }
        if (this.accountName === 'TUM') {
            this.usernamePlaceholder = 'global.form.username.tumPlaceholder';
            this.errorMessageUsername = 'home.errors.tumWarning';
            // Temporary workaround: Do not show a warning when TUM users login with an email address with a specific ending
            // allow emails with exactly one @ and usernames between 7 and 50 characters (shorter TUM usernames are not possible)
            this.usernameRegexPattern = new RegExp(/^(?!.*@.*@)[a-zA-Z0-9.@_-]{7,50}$/);
        }
        this.usernamePlaceholderTranslated = this.translateService.instant(this.usernamePlaceholder);
        this.translateService.onLangChange.subscribe(() => {
            this.usernamePlaceholderTranslated = this.translateService.instant(this.usernamePlaceholder);
        });

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
            .catch(() => {
                this.authenticationError = true;
            })
            .finally(() => (this.isSubmittingLogin = false));
    }

    /**
     * Handle a successful user login.
     */
    private handleLoginSuccess() {
        this.authenticationError = false;

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
     */
    private handleOrionLogin() {
        if (!isOrion) {
            return;
        }

        const modalRef: NgbModalRef = this.modalService.open(ModalConfirmAutofocusComponent as Component, {
            size: 'lg',
            backdrop: 'static',
        });
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
            // previousState was set in the authExpiredInterceptor before being redirected to the login modal.
            // since login is successful, go to stored previousState and clear previousState
            const redirect = this.stateStorageService.getUrl();
            if (redirect && redirect !== '') {
                this.stateStorageService.storeUrl('');
                this.router.navigateByUrl(redirect);
            } else {
                this.router.navigate(['courses']);
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

    checkFormValidity() {
        this.isFormValid =
            this.username !== undefined &&
            this.username.length >= this.USERNAME_MIN_LENGTH &&
            this.username.length <= this.USERNAME_MAX_LENGTH &&
            this.password !== undefined &&
            this.password.length >= this.PASSWORD_MIN_LENGTH &&
            this.password.length <= this.PASSWORD_MAX_LENGTH;
    }
}
