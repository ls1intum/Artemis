import { Injectable, inject } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { Router } from '@angular/router';
import { finalize } from 'rxjs/operators';

import { AuthServerProvider, Credentials } from 'app/core/auth/auth-jwt.service';
import { AccountService } from 'app/core/auth/account.service';

@Injectable({ providedIn: 'root' })
export class LoginService {
    logoutWasForceful = false;

    private accountService = inject(AccountService);
    private authServerProvider = inject(AuthServerProvider);
    private router = inject(Router);
    private alertService = inject(AlertService);

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

        if (wasInitiatedByUser) {
            this.authServerProvider
                .logout()
                .pipe(
                    finalize(() => {
                        this.onLogout();
                    }),
                )
                .subscribe();
        } else {
            this.onLogout();
        }
    }

    private onLogout(): void {
        this.accountService.authenticate(undefined);
        this.alertService.closeAll();
        this.router.navigateByUrl('/');
    }

    lastLogoutWasForceful(): boolean {
        return this.logoutWasForceful;
    }
}
