import { Component, OnInit, AfterViewInit, Renderer, ElementRef } from '@angular/core';
import { NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';
import { Router } from '@angular/router';

import { AccountService, Credentials, LoginService, StateStorageService, User } from '../core';
import { HttpErrorResponse } from '@angular/common/http';

@Component({
    selector: 'jhi-home',
    templateUrl: './home.component.html',
    styleUrls: ['home.scss']
})
export class HomeComponent implements OnInit, AfterViewInit {
    authenticationError = false;
    authenticationAttempts = 0;
    account: User;
    modalRef: NgbModalRef;
    password: string;
    rememberMe = true;
    username: string;
    captchaRequired = false;
    credentials: Credentials;

    constructor(
        private router: Router,
        private accountService: AccountService,
        private loginService: LoginService,
        private stateStorageService: StateStorageService,
        private elementRef: ElementRef,
        private renderer: Renderer,
        private eventManager: JhiEventManager
    ) {}

    ngOnInit() {
        this.accountService.identity().then(user => {
            this.currentUserCallback(user);
        });
        this.registerAuthenticationSuccess();
    }

    registerAuthenticationSuccess() {
        this.eventManager.subscribe('authenticationSuccess', (message: string) => {
            this.accountService.identity().then(user => {
                this.currentUserCallback(user);
            });
        });
    }

    ngAfterViewInit() {
        this.renderer.invokeElementMethod(this.elementRef.nativeElement.querySelector('#username'), 'focus', []);
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

    currentUserCallback(account: User) {
        this.account = account;
        if (account) {
            this.router.navigate(['courses']);
        }
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

    isAuthenticated() {
        return this.accountService.isAuthenticated();
    }

    register() {
        this.router.navigate(['/register']);
    }

    requestResetPassword() {
        this.router.navigate(['/reset', 'request']);
    }
}
