import { Injectable, isDevMode } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';

import { AccountService } from '../';
import { LoginModalService } from '../login/login-modal.service';
import { StateStorageService } from './state-storage.service';
import { LocalStorageService } from 'ngx-webstorage';

@Injectable({ providedIn: 'root' })
export class UserRouteAccessService implements CanActivate {
    constructor(
        private router: Router,
        private loginModalService: LoginModalService,
        private accountService: AccountService,
        private stateStorageService: StateStorageService,
        private localStorage: LocalStorageService
    ) {}

    canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean | Promise<boolean> {
        // save the jwt token from get parameter for lti launch requests
        if (route.routeConfig.path === 'courses/:courseId/exercise/:exerciseId' && route.queryParams['jwt']) {
            const jwt = route.queryParams['jwt'];
            this.localStorage.store('authenticationToken', jwt);
        }

        const authorities = route.data['authorities'];
        // We need to call the checkLogin / and so the accountService.identity() function, to ensure,
        // that the client has an account too, if they already logged in by the server.
        // This could happen on a page refresh.
        return this.checkLogin(authorities, state.url);
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
                        this.loginModalService.open();
                    }
                });
                return false;
            })
        );
    }
}
