import { Service, inject } from '@angular/core';
import { AlertService } from 'app/foundation/service/alert.service';
import { Router } from '@angular/router';
import { finalize } from 'rxjs/operators';

import { AuthServerProvider, Credentials } from 'app/core/auth/auth-jwt.service';
import { AccountService } from 'app/core/auth/account.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';

@Service()
export class LoginService {
    private accountService = inject(AccountService);
    private authServerProvider = inject(AuthServerProvider);
    private router = inject(Router);
    private alertService = inject(AlertService);
    private sessionStorageService = inject(SessionStorageService);

    logoutWasForceful = false;

    /**
     * Login the user with the given credentials.
     * @param credentials {Credentials} Credentials of the user to login.
     */
    login(credentials: Credentials) {
        return new Promise<void>((resolve, reject) => {
            this.authServerProvider.login(credentials).subscribe({
                next: () => {
                    void this.accountService.identity(true).then(() => {
                        resolve();
                    });
                },
                error: (err) => {
                    this.logout(false);
                    this.logoutWasForceful = false;
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
                    void this.accountService.identity(true).then(() => {
                        resolve();
                    });
                },
                error: (err) => {
                    this.logout(false);
                    this.logoutWasForceful = false;
                    reject(err);
                },
            });
        });
    }

    /**
     * Login the user with OIDC.
     * @param rememberMe whether or not to remember the user
     */
    loginOIDC(rememberMe: boolean): Promise<void> {
        return new Promise<void>((resolve, reject) => {
            try {
                this.authServerProvider.loginOIDC(rememberMe);
                resolve();
            } catch (err) {
                this.logoutWasForceful = false;
                reject(err);
            }
        });
    }

    /**
     * Log out the user and remove all traces of the login from the browser:
     * Tokens, Alerts, User object in memory.
     * Will redirect to home when done.
     */
    logout(wasInitiatedByUser: boolean) {
        this.logoutWasForceful = !wasInitiatedByUser;

        if (wasInitiatedByUser) {
            this.logoutOnServer();
        } else {
            this.onLogout();
        }
    }

    /**
     * Log out a user whose session was ended by the server although the token itself is still valid (credentials changed after the session started).
     * Unlike a forceful logout without server call, this also asks the server to clear the token cookie, otherwise the old cookie would still
     * authenticate the user on the sign-in page. The logout still counts as forceful, so the sign-in page explains that the session ended.
     * @param previousUrl the url to return to after signing in again. It is stored only once the logout finished, because the logout clears the session storage.
     */
    logoutWithServerSession(previousUrl: string) {
        this.logoutWasForceful = true;
        this.logoutOnServer(() => this.sessionStorageService.store('previousUrl', previousUrl));
    }

    /**
     * Asks the server to end the session and then removes the local traces of it. The local logout runs even if the request fails: the browser must not keep presenting
     * a user as signed in whose session the server just rejected or who asked to leave. What an outdated session may do is decided by the server on every request that
     * creates a credential, not by the cookie, so a cookie that stays after a failed request grants nothing new and the next refused request ends the session again.
     */
    private logoutOnServer(afterLogout?: () => void): void {
        this.authServerProvider
            .logout()
            .pipe(
                finalize(() => {
                    this.onLogout();
                    afterLogout?.();
                }),
            )
            .subscribe({
                error: () => {
                    // the local logout in finalize is the handling, a failed request is not rethrown as an unhandled error
                },
            });
    }

    private onLogout(): void {
        this.accountService.authenticate(undefined);
        this.alertService.closeAll();
        void this.router.navigateByUrl('/sign-in');
        this.authServerProvider.clearCaches().subscribe();
    }

    lastLogoutWasForceful(): boolean {
        return this.logoutWasForceful;
    }
}
