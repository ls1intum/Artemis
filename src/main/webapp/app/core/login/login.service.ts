import { Injectable } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { Router } from '@angular/router';
import { EMPTY, from } from 'rxjs';
import { catchError, switchMap, tap } from 'rxjs/operators';

import { AuthServerProvider, Credentials } from 'app/core/auth/auth-jwt.service';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { NotificationService } from 'app/shared/notification/notification.service';

@Injectable({ providedIn: 'root' })
export class LoginService {
    constructor(
        private accountService: AccountService,
        private websocketService: JhiWebsocketService,
        private authServerProvider: AuthServerProvider,
        private router: Router,
        private alertService: JhiAlertService,
        private notificationService: NotificationService,
    ) {}

    /**
     * Login the user with the given credentials.
     * @param credentials {Credentials} Credentials of the user to login.
     * @param callback The callback function to use (optional)
     */
    login(credentials: Credentials, callback?: any) {
        const cb = callback || function () {};

        return new Promise((resolve, reject) => {
            this.authServerProvider.login(credentials).subscribe(
                (data) => {
                    this.accountService.identity(true).then(() => {
                        resolve(data);
                    });
                    return cb();
                },
                (err) => {
                    this.logout();
                    reject(err);
                    return cb(err);
                },
            );
        });
    }

    /**
     * Login the user with SAML2.
     * 
     * @param callback The callback function to use (optional)
     */
    loginSAML2(callback?: any) {
        const cb = callback || function () {};

        return new Promise((resolve, reject) => {
            this.authServerProvider.loginSAML2().subscribe(
                (data) => {
                    this.accountService.identity(true).then(() => {
                        resolve(data);
                    });
                    return cb();
                },
                (err) => {
                    this.logout();
                    reject(err);
                    return cb(err);
                },
            );
        });
    }

    /**
     * Login with JWT token.
     * @param jwt {string} The JWT token.
     * @param rememberMe {boolean} True to save JWT in localStorage, false to save it in sessionStorage.
     */
    loginWithToken(jwt: string, rememberMe: boolean) {
        return this.authServerProvider.loginWithToken(jwt, rememberMe);
    }

    /**
     * Log out the user and remove all traces of the login from the browser:
     * Tokens, Alerts, User object in memory.
     * Will redirect to home when done.
     */
    logout() {
        this.authServerProvider
            // 1: Clear the auth tokens from the browser's caches.
            .removeAuthTokenFromCaches()
            .pipe(
                // 2: Clear all other caches (this is important so if a new user logs in, no old values are available
                tap(() => {
                    return this.authServerProvider.clearCaches();
                }),
                // 3: Set the user's auth object to null as components might have to act on the user being logged out.
                tap(() => {
                    return this.accountService.authenticate(undefined);
                }),
                // 4: Clear all existing alerts of the user.
                tap(() => {
                    return this.alertService.clear();
                }),
                // 5: Clean up notification service.
                tap(() => {
                    return this.notificationService.cleanUp();
                }),
                // 6: Navigate to the login screen.
                switchMap(() => {
                    return from(this.router.navigateByUrl('/'));
                }),
                // If something happens during the logout, show the error to the user.
                catchError((error: any) => {
                    this.alertService.error('logout.failed', { error });
                    return EMPTY;
                }),
            )
            .subscribe();
    }
}
