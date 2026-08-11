import { AfterViewChecked, Component, DestroyRef, ElementRef, OnDestroy, OnInit, Renderer2, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { User } from 'app/account/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { LoginService } from 'app/core/login/login.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MODULE_FEATURE_PASSKEY, PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH, USERNAME_MAX_LENGTH, USERNAME_MIN_LENGTH } from 'app/app.constants';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { faArrowLeft, faCircleNotch, faKey } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Saml2LoginComponent } from './saml2-login/saml2-login.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { WebauthnService } from 'app/account/user/settings/passkey-settings/webauthn.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { HttpClient } from '@angular/common/http';
import { LoginOptionsDTO } from '../auth/login-options.model';
import { TumUiButtonComponent, TumUiCheckboxComponent, TumUiInputDirective, TumUiMessageComponent } from '@tumaet/ui-angular';
import { NgTemplateOutlet } from '@angular/common';

@Component({
    selector: 'jhi-home',
    templateUrl: './home.component.html',
    styleUrls: ['home.scss'],
    imports: [
        TranslateDirective,
        FormsModule,
        RouterLink,
        FaIconComponent,
        Saml2LoginComponent,
        TumUiButtonComponent,
        TumUiInputDirective,
        TumUiCheckboxComponent,
        TumUiMessageComponent,
        NgTemplateOutlet,
    ],
})
export class HomeComponent implements OnInit, AfterViewChecked, OnDestroy {
    protected readonly faCircleNotch = faCircleNotch;
    protected readonly faKey = faKey;
    protected readonly faArrowLeft = faArrowLeft;

    private readonly router = inject(Router);
    private readonly activatedRoute = inject(ActivatedRoute);
    private readonly accountService = inject(AccountService);
    private readonly loginService = inject(LoginService);
    private readonly sessionStorageService = inject(SessionStorageService);
    private readonly renderer = inject(Renderer2);
    private readonly eventManager = inject(EventManager);
    private readonly profileService = inject(ProfileService);
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);
    private readonly webauthnService = inject(WebauthnService);
    private readonly destroyRef = inject(DestroyRef);
    private readonly http = inject(HttpClient);

    readonly usernameInput = viewChild<ElementRef<HTMLInputElement>>('usernameInput');
    readonly passwordInput = viewChild<ElementRef<HTMLInputElement>>('passwordInput');

    private isUsingFallbackIdpName = false;

    USERNAME_MIN_LENGTH = USERNAME_MIN_LENGTH;
    USERNAME_MAX_LENGTH = USERNAME_MAX_LENGTH;
    PASSWORD_MIN_LENGTH = PASSWORD_MIN_LENGTH;
    PASSWORD_MAX_LENGTH = PASSWORD_MAX_LENGTH;
    readonly authenticationError = signal(false);
    readonly account = signal<User | undefined>(undefined);
    password = '';
    rememberMe = true;

    readonly currentStage = signal<1 | 2>(1);
    readonly loginMethod = signal<'PASSWORD' | 'OIDC' | 'SAML2' | undefined>(undefined);
    readonly externalIdpName = signal<string | null>(null);
    readonly isCheckingIdentifier = signal(false);
    readonly isIdentifierValid = signal(false);

    // in case this is activated (see application-artemis.yml), users have to actively click into it
    readonly needsToAcceptTerms = signal(false);
    userAcceptedTerms = false;
    username = '';
    readonly isRegistrationEnabled = signal(false);
    readonly isPasskeyEnabled = signal(false);
    readonly loading = signal(true);
    mainElementFocused = false;

    usernamePlaceholder = 'global.form.username.placeholder'; // default, might be overridden
    readonly usernamePlaceholderTranslated = signal('Login or email'); // default, might be overridden
    // if the server is not connected to an external user management, we accept all valid username patterns
    readonly usernameRegexPattern = signal<RegExp>(/^[a-zA-Z0-9.@_-]{4,50}$/); // default (at least 4, at most 50 characters), might be overridden
    readonly errorMessageUsername = signal('home.errors.usernameIncorrect'); // default, might be overridden
    readonly accountName = signal<string | undefined>(undefined); // additional information in the welcome message

    readonly isSubmittingLogin = signal(false);
    readonly profileInfo = signal<ProfileInfo>(undefined!);

    private onPageShow = (event: PageTransitionEvent) => {
        if (event.persisted) {
            // on page update user should experience no loading
            this.isSubmittingLogin.set(false);
            this.isCheckingIdentifier.set(false);
        }
    };

    ngOnInit() {
        window.addEventListener('pageshow', this.onPageShow);
        this.initializeWithProfileInfo();
        void this.accountService.identity().then((user) => {
            this.currentUserCallback(user!);

            // Only start conditional mediation after confirming the user is NOT logged in.
            // Starting it before the identity check resolves causes race conditions during
            // logout: the component is briefly created while still authenticated, fires a
            // challenge request, gets destroyed, and a new instance overwrites the cookie.
            if (!user) {
                this.loading.set(false);
                void this.prefillPasskeysIfPossible();
            }
        });
        this.registerAuthenticationSuccess();

        const prefilledUsername = this.accountService.getAndClearPrefilledUsername();
        if (prefilledUsername) {
            this.username = prefilledUsername;
            this.checkIdentifierValidity();
        }
    }

    ngOnDestroy() {
        this.webauthnService.stopConditionalMediation();
    }

    /**
     * Initiates passkey autofill via conditional mediation if the browser supports it.
     * Delegates lifecycle management to WebauthnService to avoid race conditions
     * when the component is rapidly destroyed and recreated (e.g., during logout).
     * @see https://www.w3.org/TR/webauthn-3/#client-side-discoverable-credential
     */
    async prefillPasskeysIfPossible() {
        if (!this.isPasskeyEnabled()) {
            return;
        }
        if (!window.PublicKeyCredential?.isConditionalMediationAvailable) {
            return;
        }
        const isAvailable = await PublicKeyCredential.isConditionalMediationAvailable();
        if (isAvailable) {
            this.webauthnService.startConditionalMediation(
                () => this.handleLoginSuccess(),
                () => this.refocusUsernameFieldForPasskeyAutofill(),
            );
        }
    }

    /**
     * Re-focuses the username field after conditional mediation becomes active.
     * The browser only shows passkey autofill suggestions when the field receives
     * focus while a conditional mediation request is pending. Since the initial
     * autofocus happens before the mediation HTTP request completes, we need to
     * re-trigger focus once the mediation is active.
     *
     * Only re-focuses if the user hasn't started interacting with the form yet.
     */
    private refocusUsernameFieldForPasskeyAutofill(): void {
        // Allow one event-loop tick so the browser fully registers the conditional mediation.
        setTimeout(() => {
            const usernameInput = this.renderer.selectRootElement('#username', true);
            if (!usernameInput) {
                return;
            }

            // Only re-focus if the username field is still the active element and the
            // user hasn't started typing — this avoids disrupting user interaction.
            if (document.activeElement === usernameInput && !this.username) {
                usernameInput.blur();
                usernameInput.focus();
            }
        });
    }

    async loginWithPasskey() {
        try {
            await this.webauthnService.loginWithPasskey();
            this.handleLoginSuccess();
        } catch (error) {
            if (this.isPasskeyLoginAbortError(error)) {
                await this.prefillPasskeysIfPossible();
                return;
            }
            throw error;
        }
    }

    private isPasskeyLoginAbortError(error: unknown): boolean {
        return error instanceof DOMException && (error.name === 'AbortError' || error.name === 'NotAllowedError');
    }

    /**
     * Initializes the component with the required information received from the server.
     */
    private initializeWithProfileInfo() {
        const profileInfo = this.profileService.getProfileInfo();
        this.profileInfo.set(profileInfo);
        this.isPasskeyEnabled.set(this.profileService.isModuleFeatureActive(MODULE_FEATURE_PASSKEY));

        this.accountName.set(profileInfo.accountName);
        if (profileInfo.allowedLdapUsernamePattern) {
            this.usernameRegexPattern.set(new RegExp(profileInfo.allowedLdapUsernamePattern));
        }
        if (this.accountName() === 'TUM') {
            this.usernamePlaceholder = 'global.form.username.tumPlaceholder';
            this.errorMessageUsername.set('home.errors.tumWarning');
            // Temporary workaround: Do not show a warning when TUM users login with an email address with a specific ending
            // allow emails with exactly one @ and usernames between 7 and 50 characters (shorter TUM usernames are not possible)
            this.usernameRegexPattern.set(new RegExp(/^(?!.*@.*@)[a-zA-Z0-9.@_-]{7,50}$/));
        }

        this.usernamePlaceholderTranslated.set(this.translateService.instant(this.usernamePlaceholder));

        // Combined single subscription to handle system language changes smoothly
        this.translateService.onLangChange.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
            this.usernamePlaceholderTranslated.set(this.translateService.instant(this.usernamePlaceholder));
            if (this.isUsingFallbackIdpName) {
                this.externalIdpName.set(this.translateService.instant('home.login.form.universityCredentials'));
            }
        });

        this.isRegistrationEnabled.set(!!profileInfo.registrationEnabled);
        this.needsToAcceptTerms.set(!!profileInfo.needsToAcceptTerms);
        this.activatedRoute.queryParams.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
            if (params['loginError'] === 'deactivated') {
                this.authenticationError.set(true);
                this.alertService.error('home.errors.loginDeactivated');
            } else if (params['loginError'] === 'oidcFailure') {
                this.authenticationError.set(true);
                this.alertService.error('home.errors.ssoFailure');
            }
        });
    }

    registerAuthenticationSuccess() {
        const subscription = this.eventManager.subscribe('authenticationSuccess', () => {
            // We only need to authenticate once, make sure we don't run this subscription multiple times
            this.eventManager.destroy(subscription);

            void this.accountService.identity().then((user) => {
                this.currentUserCallback(user!);
            });
        });
    }

    ngAfterViewChecked() {
        // Only focus the username input once, not on every update
        if (this.mainElementFocused || this.loading()) {
            return;
        }
        if (this.currentStage() === 1) {
            const usernameEl = this.usernameInput();
            if (usernameEl?.nativeElement) {
                usernameEl.nativeElement.focus();
                this.mainElementFocused = true;
            }
        } else if (this.currentStage() === 2) {
            const passwordEl = this.passwordInput();
            if (this.loginMethod() === 'PASSWORD' && passwordEl?.nativeElement) {
                passwordEl.nativeElement.focus();
                this.mainElementFocused = true;
            } else {
                this.mainElementFocused = true;
            }
        }

        // If the session expired or similar display a warning
        if (this.loginService.lastLogoutWasForceful()) {
            this.alertService.error('home.errors.sessionExpired');
        }
    }

    /**
     * Checks the identifier and fetches login options from the server side.
     */
    onContinue() {
        if (!this.username || !this.isIdentifierValid()) {
            return;
        }

        this.isCheckingIdentifier.set(true);

        this.http
            .get<LoginOptionsDTO>('api/core/public/login-options', {
                params: { usernameOrEmail: this.username },
            })
            .subscribe({
                next: (options) => {
                    this.isCheckingIdentifier.set(false);

                    const allowedMethods: ('PASSWORD' | 'OIDC' | 'SAML2')[] = ['PASSWORD', 'OIDC', 'SAML2'];
                    const resolvedMethod = allowedMethods.includes(options.loginMethod) ? options.loginMethod : 'PASSWORD';

                    this.loginMethod.set(resolvedMethod);

                    // If no university IdP name was provided
                    let resolvedIdpName = options.idpName?.trim();
                    if (!resolvedIdpName) {
                        this.isUsingFallbackIdpName = true;
                        resolvedIdpName = this.translateService.instant('home.login.form.universityCredentials') as string;
                    } else {
                        this.isUsingFallbackIdpName = false;
                    }
                    this.externalIdpName.set(resolvedIdpName);

                    this.currentStage.set(2);
                    this.authenticationError.set(false);
                    this.mainElementFocused = false;
                },
                error: () => {
                    this.isCheckingIdentifier.set(false);
                    this.loginMethod.set('PASSWORD');
                    this.externalIdpName.set(null);
                    this.currentStage.set(2);
                    this.mainElementFocused = false;
                },
            });
    }

    /**
     * Resets the authentication stage back to the initial identifier screen
     * and clears the password input.
     */
    goBack() {
        this.currentStage.set(1);
        this.password = '';
        this.mainElementFocused = false;
    }

    /**
     * Executes the traditional password-based authentication flow.
     */
    login() {
        this.isSubmittingLogin.set(true);
        this.loginService
            .login({
                username: this.username,
                password: this.password,
                rememberMe: this.rememberMe,
            })
            .then(() => {
                this.handleLoginSuccess();
            })
            .catch(() => {
                this.authenticationError.set(true);
            })
            .finally(() => {
                this.isSubmittingLogin.set(false);
            });
    }

    /**
     * Initiates the OpenID Connect (OIDC) single sign-on authentication flow.
     */
    loginWithOidc() {
        this.isSubmittingLogin.set(true);
        this.loginService
            .loginOIDC(this.rememberMe)
            .catch(() => {
                this.authenticationError.set(true);
            })
            .finally(() => {
                this.isSubmittingLogin.set(false);
            });
    }

    /**
     * Handle a successful user login.
     */
    private handleLoginSuccess() {
        this.authenticationError.set(false);

        if (this.router.url === '/register' || /^\/activate\//.test(this.router.url) || /^\/reset\//.test(this.router.url)) {
            void this.router.navigate(['']);
        }

        this.eventManager.broadcast({
            name: 'authenticationSuccess',
            content: 'Sending Authentication Success',
        });
    }

    currentUserCallback(account: User) {
        this.account.set(account);
        if (account) {
            // previousState was set in the authExpiredInterceptor before being redirected to the login modal.
            // since login is successful, go to the stored previousState and clear the previousState
            const redirect = this.sessionStorageService.retrieve<string>('previousUrl');
            if (redirect && redirect !== '') {
                this.sessionStorageService.store('previousUrl', '');
                void this.router.navigateByUrl(redirect);
            } else {
                void this.router.navigate(['courses']);
            }
        }
    }

    inputChange(event: Event) {
        const target = event.target as HTMLInputElement | null;
        if (target && target.name === 'username') {
            this.username = target.value;
        }
        if (target && target.name === 'password') {
            this.password = target.value;
        }
    }

    /**
     * Validates if the currently entered username or email satisfies both
     * the length constraints and the dynamic regular expression pattern.
     */
    checkIdentifierValidity() {
        const meetsLength = this.username !== undefined && this.username.length >= this.USERNAME_MIN_LENGTH && this.username.length <= this.USERNAME_MAX_LENGTH;

        this.isIdentifierValid.set(meetsLength && this.usernameRegexPattern().test(this.username));
    }
}
