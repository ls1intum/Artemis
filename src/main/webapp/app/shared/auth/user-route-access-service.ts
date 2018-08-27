import { Inject, Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';

import { Principal } from '../';
import { LoginModalService } from '../login/login-modal.service';
import { StateStorageService } from './state-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { NG1AUTH_SERVICE } from '../../shared/auth/ng1-auth-wrapper.service';

@Injectable()
export class UserRouteAccessService implements CanActivate {

    constructor(private router: Router,
                private loginModalService: LoginModalService,
                private principal: Principal,
                private stateStorageService: StateStorageService,
                private localStorage: LocalStorageService,
                @Inject(NG1AUTH_SERVICE) private ng1AuthService: any) {
    }

    canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean | Promise<boolean> {

        // save the jwt token from get parameter for lti launch requests
        if (route.routeConfig.path === 'courses/:courseId/exercise/:exerciseId' && route.queryParams['jwt']) {
            const jwt = route.queryParams['jwt'];
            this.localStorage.store('authenticationToken', jwt);
            this.ng1AuthService.login({rememberMe: true}, jwt);
        }

        const authorities = route.data['authorities'];
        // We need to call the checkLogin / and so the principal.identity() function, to ensure,
        // that the client has a principal too, if they already logged in by the server.
        // This could happen on a page refresh.
        return this.checkLogin(authorities, state.url);
    }

    checkLogin(authorities: string[], url: string): Promise<boolean> {
        const principal = this.principal;
        return Promise.resolve(principal.identity().then(account => {

            if (!authorities || authorities.length === 0) {
                return true;
            }

            if (account) {
                return principal.hasAnyAuthority(authorities).then(response => {
                    return response;
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
        }));
    }
}
