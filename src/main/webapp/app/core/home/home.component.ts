import { AfterViewChecked, Component, OnInit, Renderer2, inject } from '@angular/core';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { User } from 'app/core/user/user.model';
import { Credentials } from 'app/core/auth/auth-jwt.service';
import { AccountService } from 'app/core/auth/account.service';
import { LoginService } from 'app/core/login/login.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { StateStorageService } from 'app/core/auth/state-storage.service';
import { FEATURE_PASSKEY, PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH, USERNAME_MAX_LENGTH, USERNAME_MIN_LENGTH } from 'app/app.constants';
import { EventManager } from 'app/shared/service/event-manager.service';
import { AlertService } from 'app/shared/service/alert.service';
import { faCircleNotch, faKey } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Saml2LoginComponent } from './saml2-login/saml2-login.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { WebauthnService } from 'app/core/user/settings/passkey-settings/webauthn.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { WebauthnApiService } from 'app/core/user/settings/passkey-settings/webauthn-api.service';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { getCredentialWithGracefullyHandlingAuthenticatorIssues } from 'app/core/user/settings/passkey-settings/util/credential.util';
import { InvalidCredentialError } from 'app/core/user/settings/passkey-settings/entities/invalid-credential-error';
import { EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY, SetupPasskeyModalComponent } from 'app/core/course/overview/setup-passkey-modal/setup-passkey-modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { PasskeyAbortError } from 'app/core/user/settings/passkey-settings/entities/passkey-abort-error';

/**
 * This occurs if a user clicks the "login with passkey" button but then cancel the login process.
 */
const USER_CANCELLED_LOGIN_WITH_PASSKEY_ERROR = 'NotAllowedError';

@Component({
    selector: 'jhi-home',
    templateUrl: './home.component.html',
    styleUrls: ['home.scss'],
    imports: [TranslateDirective, FormsModule, RouterLink, FaIconComponent, Saml2LoginComponent, ButtonComponent],
})
export class HomeComponent implements OnInit, AfterViewChecked {
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faKey = faKey;
    protected readonly ButtonSize = ButtonSize;
    protected readonly ButtonType = ButtonType;

    private router = inject(Router);
    private activatedRoute = inject(ActivatedRoute);
    private accountService = inject(AccountService);
    private loginService = inject(LoginService);
    private stateStorageService = inject(StateStorageService);
    private renderer = inject(Renderer2);
    private eventManager = inject(EventManager);
    private profileService = inject(ProfileService);
    private alertService = inject(AlertService);
    private translateService = inject(TranslateService);
    private webauthnService = inject(WebauthnService);
    private webauthnApiService = inject(WebauthnApiService);
    private modalService = inject(NgbModal);

    protected usernameTouched = false;
    protected passwordTouched = false;

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
    isPasskeyEnabled = false;
    loading = true;
    mainElementFocused = false;

    usernamePlaceholder = 'global.form.username.placeholder'; // default, might be overridden
    usernamePlaceholderTranslated = 'Login or email'; // default, might be overridden
    // if the server is not connected to an external user management, we accept all valid username patterns
    usernameRegexPattern = /^[a-zA-Z0-9.@_-]{4,50}$/; // default (at least 4, at most 50 characters), might be overridden
    errorMessageUsername = 'home.errors.usernameIncorrect'; // default, might be overridden
    accountName?: string; // additional information in the welcome message

    isFormValid = false;
    isSubmittingLogin = false;

    profileInfo: ProfileInfo;

    /**
     * <p>
     * We want users to use passkey authentication over password authentication.
     * </p>
     * <p>
     * If the passkey feature is enabled and no passkeys are set up yet, we display a modal that informs the user about passkeys and forwards to the setup page.
     * </p>
     */
    openSetupPasskeyModal(): void {
        if (!this.isPasskeyEnabled) {
            return;
        }

        const earliestReminderDate = localStorage.getItem(EARLIEST_SETUP_PASSKEY_REMINDER_DATE_LOCAL_STORAGE_KEY);
        const userDisabledReminderForCurrentTimeframe = earliestReminderDate && new Date() < new Date(earliestReminderDate);
        if (userDisabledReminderForCurrentTimeframe) {
            return;
        }

        if (!this.accountService.userIdentity?.askToSetupPasskey) {
            return;
        }

        this.modalService.open(SetupPasskeyModalComponent, { size: 'lg', backdrop: 'static' });
    }

    ngOnInit() {
        this.initializeWithProfileInfo();
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

        this.prefillPasskeysIfPossible();
    }

    /**
     * Makes sure that passkeys can be autofilled on <a href="https://caniuse.com/mdn-api_publickeycredential_isconditionalmediationavailable_static">supporting browsers</a>.
     */
    async prefillPasskeysIfPossible() {
        if (window.PublicKeyCredential && PublicKeyCredential.isConditionalMediationAvailable) {
            const isConditionalMediationAvailable = await PublicKeyCredential.isConditionalMediationAvailable();
            if (isConditionalMediationAvailable) {
                await this.makePasskeyAutocompleteAvailable();
            }
        }
    }

    /**
     * Ensures that passkey autocomplete functionality is available by initiating a credential request
     * with conditional mediation. This is required to enable the browser's passkey autocomplete feature.
     */
    async makePasskeyAutocompleteAvailable() {
        await this.loginWithPasskey(true);
    }

    /**
     * Performs a passkey login. This includes requesting the passkey credential from the authenticator.
     *
     * @param isConditional set to true if the passkey credential shall be requested with conditional mediation (required for the passkey autocomplete)
     */
    async loginWithPasskey(isConditional: boolean = false) {
        try {
            const authenticatorCredential = await this.webauthnService.getCredential(isConditional);

            if (!authenticatorCredential || authenticatorCredential.type != 'public-key') {
                // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
                throw new InvalidCredentialError();
            }

            const credential = getCredentialWithGracefullyHandlingAuthenticatorIssues(authenticatorCredential) as unknown as PublicKeyCredential;
            if (!credential) {
                // noinspection ExceptionCaughtLocallyJS - intended to be caught locally
                throw new InvalidCredentialError();
            }

            await this.webauthnApiService.loginWithPasskey(credential);
            this.handleLoginSuccess();
        } catch (error) {
            const shouldFailErrorSilently = this.isPasskeyAutocompleteError(error);
            if (shouldFailErrorSilently) {
                return;
            }

            if (error instanceof InvalidCredentialError) {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.invalidCredential');
            } else {
                this.alertService.addErrorAlert('artemisApp.userSettings.passkeySettingsPage.error.login');
            }
            // eslint-disable-next-line no-undef
            console.error(error);
            throw error;
        }
    }

    /**
     * Checks if the error is related to passkey autocomplete and makes sure to reEnable it if necessary.
     *
     * @param error that occurred during the passkey login process.
     * @return true if the error is an expected error related to making passkey autocomplete available
     */
    private isPasskeyAutocompleteError(error: Error | string): boolean {
        if (error instanceof Error && error.name === USER_CANCELLED_LOGIN_WITH_PASSKEY_ERROR) {
            // The user manually aborted the passkey login process after clicking the "Sign in with passkey" button.
            // This required aborting the `getCredential` request with conditional mediation, which is necessary for enabling passkey autocomplete.
            // To restore passkey autocomplete functionality, re-invoke `makePasskeyAutocompleteAvailable`.
            // eslint-disable-next-line no-undef
            console.warn('Operation not allowed or timed out: login with passkey was aborted manually by the user');
            this.makePasskeyAutocompleteAvailable();
            return true;
        } else if (error instanceof PasskeyAbortError) {
            // eslint-disable-next-line no-undef
            console.warn(error.message);
            return true;
        } else if (error instanceof Error && error.name === 'OperationError' && error.message === 'A request is already pending.') {
            // This error occurs after logging out in connection with makePasskeyAutocompleteAvailable, we want to fail silently in that case
            // eslint-disable-next-line no-undef
            console.warn(error.message);
            return true;
        }

        throw false;
    }

    /**
     * Initializes the component with the required information received from the server.
     */
    private initializeWithProfileInfo() {
        this.profileInfo = this.profileService.getProfileInfo();
        this.isPasskeyEnabled = this.profileService.isModuleFeatureActive(FEATURE_PASSKEY);

        this.accountName = this.profileInfo.accountName;
        if (this.profileInfo.allowedLdapUsernamePattern) {
            this.usernameRegexPattern = new RegExp(this.profileInfo.allowedLdapUsernamePattern);
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

        this.isRegistrationEnabled = !!this.profileInfo.registrationEnabled;
        this.needsToAcceptTerms = !!this.profileInfo.needsToAcceptTerms;
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
            .then(() => {
                this.handleLoginSuccess();
                this.openSetupPasskeyModal();
            })
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
    }

    currentUserCallback(account: User) {
        this.account = account;
        if (account) {
            // previousState was set in the authExpiredInterceptor before being redirected to the login modal.
            // since login is successful, go to the stored previousState and clear the previousState
            const redirect = this.stateStorageService.getUrl();
            if (redirect && redirect !== '') {
                this.stateStorageService.storeUrl('');
                this.router.navigateByUrl(redirect);
            } else {
                this.router.navigate(['courses']);
            }
        }
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
