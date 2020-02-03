import { AfterViewInit, Component, ElementRef, OnInit, Renderer2 } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';
import { Router } from '@angular/router';

import { StateStorageService } from '../core';
import { User } from 'app/core/user/user.model';
import { Credentials } from 'app/core/auth/auth-jwt.service';
import { HttpErrorResponse } from '@angular/common/http';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { JavaBridgeService } from 'app/intellij/java-bridge.service';
import { isIntelliJ } from 'app/intellij/intellij';
import { ModalConfirmAutofocusComponent } from 'app/intellij/modal-confirm-autofocus/modal-confirm-autofocus.component';
import { AccountService } from 'app/core/auth/account.service';
import { LoginService } from 'app/core/login/login.service';
import { TUM_USERNAME_REGEX } from 'app/app.constants';
import { ProfileService } from 'app/layouts/profiles/profile.service';
import { filter, tap } from 'rxjs/operators';
import { ProfileInfo } from 'app/layouts';

@Component({
    selector: 'jhi-home',
    templateUrl: './home.component.html',
    styleUrls: ['home.scss'],
})
export class HomeComponent implements OnInit, AfterViewInit {
    authenticationError = false;
    authenticationAttempts = 0;
    account: User;
    modalRef: NgbModalRef;
    password: string;
    rememberMe = true;
    acceptTerms = true;
    username: string;
    captchaRequired = false;
    credentials: Credentials;

    usernameRegexPattern = TUM_USERNAME_REGEX; // default, might be overridden in ngOnInit
    signInMessage = 'home.pleaseSignInTUM'; // default, might be overridden in ngOnInit
    errorMesssageUsername = 'home.errors.tumWarning'; // default, might be overridden in ngOnInit

    isSubmittingLogin = false;

    constructor(
        private router: Router,
        private accountService: AccountService,
        private loginService: LoginService,
        private stateStorageService: StateStorageService,
        private elementRef: ElementRef,
        private renderer: Renderer2,
        private eventManager: JhiEventManager,
        private guidedTourService: GuidedTourService,
        private javaBridge: JavaBridgeService,
        private modalService: NgbModal,
        private profileService: ProfileService,
    ) {}

    ngOnInit() {
        this.profileService
            .getProfileInfo()
            .pipe(
                filter(Boolean),
                tap((info: ProfileInfo) => {
                    if (!info.activeProfiles.includes('jira')) {
                        // if the server is not connected to an external user management such as JIRA, we accept all valid username patterns
                        this.usernameRegexPattern = /^[a-z0-9_-]{3,100}$/;
                        this.signInMessage = 'home.pleaseSignIn';
                        this.errorMesssageUsername = 'home.errors.usernameIncorrect';
                    }
                }),
            )
            .subscribe();
        this.accountService.identity().then(user => {
            this.currentUserCallback(user!);
        });
        this.registerAuthenticationSuccess();
    }

    registerAuthenticationSuccess() {
        this.eventManager.subscribe('authenticationSuccess', (message: string) => {
            this.accountService.identity().then(user => {
                this.currentUserCallback(user!);
            });
        });
    }

    ngAfterViewInit() {
        this.renderer.selectRootElement('#username', true).focus();
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

                // previousState was set in the authExpiredInterceptor before being redirected to login modal.
                // since login is successful, go to stored previousState and clear previousState
                const redirect = this.stateStorageService.getUrl();
                if (redirect) {
                    this.stateStorageService.storeUrl(null);
                    this.router.navigate([redirect]);
                }

                // Log in to IntelliJ
                if (isIntelliJ) {
                    const modalRef: NgbModalRef = this.modalService.open(ModalConfirmAutofocusComponent as Component, { size: 'lg', backdrop: 'static' });
                    modalRef.componentInstance.text = 'login.ide.confirmation';
                    modalRef.componentInstance.title = 'login.ide.title';
                    modalRef.result.then(
                        result => {
                            this.javaBridge.login(this.username, this.password);
                        },
                        reason => {},
                    );
                }
            })
            .catch((error: HttpErrorResponse) => {
                this.captchaRequired = error.headers.get('X-artemisApp-error') === 'CAPTCHA required';
                this.authenticationError = true;
                this.authenticationAttempts++;
            })
            .finally(() => (this.isSubmittingLogin = false));
    }

    currentUserCallback(account: User) {
        this.account = account;
        if (account) {
            this.router.navigate(['overview']);
        }
    }

    cancel() {
        this.credentials = {
            username: null,
            password: null,
            rememberMe: true,
        };
        this.captchaRequired = false;
        this.authenticationError = false;
        this.authenticationAttempts = 0;
    }

    isAuthenticated() {
        return this.accountService.isAuthenticated();
    }

    register() {
        this.router.navigate(['/register']);
    }

    requestResetPassword() {
        this.router.navigate(['/reset', 'request']);
    }
    inputChange($event: any) {
        if ($event.target && $event.target.name === 'username') {
            this.username = $event.target.value;
        }
        if ($event.target && $event.target.name === 'password') {
            this.password = $event.target.value;
        }
    }
}
