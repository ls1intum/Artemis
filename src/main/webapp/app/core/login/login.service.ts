import { Injectable } from '@angular/core';
import { JhiAlertService } from 'ng-jhipster';
import { Router } from '@angular/router';
import { EMPTY, from, throwError, Observable } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';

import { AuthServerProvider, Credentials } from 'app/core/auth/auth-jwt.service';
import { LocalStorageService } from 'ngx-webstorage';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { AccountService } from 'app/core/auth/account.service';
import { CUSTOM_STUDENT_LOGIN_KEY } from 'app/app.constants';

@Injectable({ providedIn: 'root' })
export class LoginService {
    constructor(
        private accountService: AccountService,
        private localStorageService: LocalStorageService,
        private websocketService: JhiWebsocketService,
        private authServerProvider: AuthServerProvider,
        private router: Router,
        private alertService: JhiAlertService,
    ) {}

    login(credentials: Credentials, callback?: any) {
        const cb = callback || function() {};

        return new Promise((resolve, reject) => {
            this.authServerProvider.login(credentials).subscribe(
                data => {
                    this.accountService.identity(true).then(user => {
                        this.websocketService.sendActivity();
                        resolve(data);
                    });
                    return cb();
                },
                err => {
                    this.logout();
                    reject(err);
                    return cb(err);
                },
            );
        });
    }

    loginWithToken(jwt: string, rememberMe: string) {
        return this.authServerProvider.loginWithToken(jwt, rememberMe);
    }

    /**
     * Log out the user and remove all traces of the login from the browser:
     * Tokens, Alerts, User object in memory.
     * Will redirect to home when done.
     */
    logout() {
        this.localStorageService.clear(CUSTOM_STUDENT_LOGIN_KEY);
        this.authServerProvider
            // 1: Clear the auth tokens from the browser's caches.
            .removeAuthTokenFromCaches()
            .pipe(
                // 2: Set the user's auth object to null as components might have to act on the user being logged out.
                tap(() => {
                    return this.accountService.authenticate(null);
                }),
                // 3: Clear all existing alerts of the user.
                tap(() => {
                    return this.alertService.clear();
                }),
                // 4: Navigate to the login screen.
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
