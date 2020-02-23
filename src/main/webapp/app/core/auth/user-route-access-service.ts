import { Injectable, isDevMode } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';
import { LocalStorageService } from 'ngx-webstorage';
import { AccountService } from 'app/core/auth/account.service';
import { StateStorageService } from 'app/core/auth/state-storage.service';
import { filter, first, switchMap } from 'rxjs/operators';
import { OrionVersionValidator } from 'app/shared/orion/outdated-plugin-warning/orion-version-validator.service';
import { from, of } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class UserRouteAccessService implements CanActivate {
    constructor(
        private router: Router,
        private accountService: AccountService,
        private stateStorageService: StateStorageService,
        private localStorage: LocalStorageService,
        private orionVersionValidator: OrionVersionValidator,
    ) {}

    canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean | Promise<boolean> {
        // save the jwt token from get parameter for lti launch requests for online course users
        // Note: The following URL has to match the redirect URL in LtiResource.java in the method launch(...) shortly before the return
        if (route.routeConfig!.path === 'overview/:courseId/exercises/:exerciseId' && route.queryParams['jwt']) {
            const jwt = route.queryParams['jwt'];
            this.localStorage.store('authenticationToken', jwt);
        }

        const authorities = route.data['authorities'];
        // We need to call the checkLogin / and so the accountService.identity() function, to ensure,
        // that the client has an account too, if they already logged in by the server.
        // This could happen on a page refresh.
        return this.checkLogin(authorities, state.url);
        // TODO return the following as soon as Orion v1.0.0 is released and the API is stable.
        // return this.orionVersionValidator
        //     .validateOrionVersion()
        //     .pipe(
        //         filter(Boolean),
        //         first(),
        //         switchMap(isValidOrNoIDE => {
        //             if (isValidOrNoIDE) {
        //                 return from(this.checkLogin(authorities, state.url));
        //             }
        //             return of(false);
        //         }),
        //     )
        //     .toPromise();
    }

    checkLogin(authorities: string[], url: string): Promise<boolean> {
        const accountService = this.accountService;
        return Promise.resolve(
            accountService.identity().then(account => {
                if (!authorities || authorities.length === 0) {
                    return true;
                }

                if (account) {
                    return accountService.hasAnyAuthority(authorities).then(response => {
                        if (response) {
                            return true;
                        }
                        if (isDevMode()) {
                            console.error('User has not any of required authorities: ', authorities);
                        }
                        return false;
                    });
                }

                this.stateStorageService.storeUrl(url);
                this.router.navigate(['accessdenied']).then(() => {
                    // only show the login dialog, if the user hasn't logged in yet
                    if (!account) {
                        this.router.navigate(['/']);
                    }
                });
                return false;
            }),
        );
    }
}
