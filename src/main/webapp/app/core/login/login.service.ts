import { Injectable } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { Router } from '@angular/router';
import { EMPTY, from } from 'rxjs';
import { catchError, switchMap, tap } from 'rxjs/operators';

import { AuthServerProvider, Credentials } from 'app/core/auth/auth-jwt.service';
import { AccountService } from 'app/core/auth/account.service';
import { JhiWebsocketService } from 'app/core/websocket/websocket.service';
import { NotificationService } from 'app/shared/notification/notification.service';

@Injectable({ providedIn: 'root' })
export class LoginService {
    logoutWasForceful = false;

    constructor(
        private accountService: AccountService,
        private websocketService: JhiWebsocketService,
        private authServerProvider: AuthServerProvider,
        private router: Router,
        private alertService: AlertService,
        private notificationService: NotificationService,
    ) {}

    /**
     * Login the user with the given credentials.
     * @param credentials {Credentials} Credentials of the user to login.
     */
    login(credentials: Credentials) {
        return new Promise<void>((resolve, reject) => {
            this.authServerProvider.login(credentials).subscribe({
                next: () => {
                    this.accountService.identity(true).then(() => {
                        resolve();
                    });
                },
                error: (err) => {
                    this.logout(false);
                    reject(err);
                },
            });
        });
    }

    /**
     * Login the user with SAML2.
     * @param rememberMe whether or not to remember the user
     */
    loginSAML2(rememberMe: boolean) {
        return new Promise<void>((resolve, reject) => {
            this.authServerProvider.loginSAML2(rememberMe).subscribe({
                next: () => {
                    this.accountService.identity(true).then(() => {
                        resolve();
                    });
                },
                error: (err) => {
                    this.logout(false);
                    reject(err);
                },
            });
        });
    }

    /**
     * Log out the user and remove all traces of the login from the browser:
     * Tokens, Alerts, User object in memory.
     * Will redirect to home when done.
     */
    logout(wasInitiatedByUser: boolean) {
        this.logoutWasForceful = !wasInitiatedByUser;

        this.authServerProvider
            // 1: Clear the auth tokens from the browser's caches.
            .logout()
            .pipe(
                // 2: Clear all other caches (this is important so if a new user logs in, no old values are available
                tap(() => {
                    if (wasInitiatedByUser) {
                        // only clear caches on an intended logout. Do not clear the caches, when the user was logged out automatically
                        return this.authServerProvider.clearCaches();
                    }
                }),
                // 3: Set the user's auth object to null as components might have to act on the user being logged out.
                tap(() => {
                    return this.accountService.authenticate(undefined);
                }),
                // 4: Clear all existing alerts of the user.
                tap(() => {
                    return this.alertService.closeAll();
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

    lastLogoutWasForceful(): boolean {
        return this.logoutWasForceful;
    }
}
