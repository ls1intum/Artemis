import { AfterViewInit, Component, ElementRef, Renderer } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Router } from '@angular/router';
import { JhiEventManager } from 'ng-jhipster';

import { LoginService } from 'app/core/login/login.service';
import { StateStorageService } from 'app/core/auth/state-storage.service';
import { Credentials } from 'app/core';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-login',
    templateUrl: './login.component.html',
})
export class JhiLoginComponent implements AfterViewInit {
    authenticationError = false;
    authenticationAttempts = 0;
    captchaRequired = false;
    password: string;
    rememberMe: boolean;
    username: string;
    credentials: Credentials;

    constructor(
        private eventManager: JhiEventManager,
        private loginService: LoginService,
        private stateStorageService: StateStorageService,
        private elementRef: ElementRef,
        private renderer: Renderer,
        private router: Router,
    ) {
        this.credentials = {
            username: null,
            password: null,
            rememberMe: null
        };
    }

    ngAfterViewInit() {
        this.renderer.invokeElementMethod(this.elementRef.nativeElement.querySelector('#username'), 'focus', []);
    }

    cancel() {
        this.credentials = {
            username: null,
            password: null,
            rememberMe: true
        };
        this.captchaRequired = false;
        this.authenticationError = false;
        this.authenticationAttempts = 0;
    }

    login() {
        this.loginService
            .login({
                username: this.username,
                password: this.password,
                rememberMe: this.rememberMe
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
                    content: 'Sending Authentication Success'
                });

                // // previousState was set in the authExpiredInterceptor before being redirected to login modal.
                // // since login is succesful, go to stored previousState and clear previousState
                const redirect = this.stateStorageService.getUrl();
                if (redirect) {
                    this.stateStorageService.storeUrl(null);
                    this.router.navigate([redirect]);
                }
            })
            .catch((error: HttpErrorResponse) => {
                if (error.headers.get('X-arTeMiSApp-error') === 'CAPTCHA required') {
                    this.captchaRequired = true;
                } else {
                    this.captchaRequired = false;
                }
                this.authenticationError = true;
                this.authenticationAttempts++;
            });
    }

    register() {
        this.router.navigate(['/register']);
    }

    requestResetPassword() {
        this.router.navigate(['/reset', 'request']);
    }
}
