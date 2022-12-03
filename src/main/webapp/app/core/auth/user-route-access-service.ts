import { Injectable, isDevMode } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';
import { LocalStorageService } from 'ngx-webstorage';
import { AccountService } from 'app/core/auth/account.service';
import { StateStorageService } from 'app/core/auth/state-storage.service';
import { OrionVersionValidator } from 'app/shared/orion/outdated-plugin-warning/orion-version-validator.service';
import { first, switchMap } from 'rxjs/operators';
import { from, lastValueFrom, of } from 'rxjs';
import { Authority } from 'app/shared/constants/authority.constants';
import { AlertService } from 'app/core/util/alert.service';

@Injectable({ providedIn: 'root' })
export class UserRouteAccessService implements CanActivate {
    constructor(
        private router: Router,
        private accountService: AccountService,
        private alertService: AlertService,
        private stateStorageService: StateStorageService,
        private localStorage: LocalStorageService,
        private orionVersionValidator: OrionVersionValidator,
    ) {}

    /**
     * Check if the client can activate a route.
     * @param route {ActivatedRouteSnapshot} The ActivatedRouteSnapshot of the route to activate.
     * @param state {RouterStateSnapshot} The current RouterStateSnapshot.
     * @return {(boolean | Promise<boolean>)} True if Orion version is valid or the connected client is a regular browser and
     * user is logged in, false otherwise.
     */
    canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean | Promise<boolean> {
        const ltiRedirectUrl = this.handleLTIRedirect(route, state);
        const urlToStore = ltiRedirectUrl ?? state.url;

        const authorities = route.data['authorities'];

        // For programming exercise template and solution participations editors shall be allowed to view the submissions, but not for other submissions.
        // To ensure this behaviour the query parameter of the route needs to be considered and the Editor authority needs to be added subsequently within the
        // canActivate check, as it can not be allowed directly within the corresponding router since this would allow access to all submissions.
        if (
            (route.routeConfig?.path === ':courseId/programming-exercises/:exerciseId/participations/:participationId/submissions' ||
                ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/participations/:participationId') &&
            route.queryParams['isTmpOrSolutionProgrParticipation'] === 'true'
        ) {
            authorities.push(Authority.EDITOR);
        }

        // We need to call the checkLogin / and so the accountService.identity() function, to ensure,
        // that the client has an account too, if they already logged in by the server.
        // This could happen on a page refresh.
        return lastValueFrom(
            this.orionVersionValidator
                // Returns true, if the Orion version is up-to-date, or the connected client is just a regular browser
                .validateOrionVersion()
                .pipe(
                    // Only take the first returned boolean and then cancel the subscription
                    first(),
                    switchMap((isValidOrNoIDE) => {
                        // 1./2. Case: The Orion version is up-to-date/The connected client is a regular browser
                        if (isValidOrNoIDE) {
                            // Always check whether the user is logged in
                            return from(this.checkLogin(authorities, urlToStore));
                        }
                        // 3. Case: The Orion Version is not up-to-date
                        return of(false);
                    }),
                ),
        );
    }

    /**
     * Deal with the redirect for LTI users: displays an alert. Removes the param so this only happens once.
     * @param route {ActivatedRouteSnapshot} The ActivatedRouteSnapshot of the route to activate.
     * @param state {RouterStateSnapshot} The current RouterStateSnapshot.
     */
    private handleLTIRedirect(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): string {
        // Note: The following URL has to match the redirect URL in LtiResource.java in the method launch(...) shortly before the return
        const regexPattern = new RegExp(/\/courses\/\d+\/exercises\/\d+/g);
        if (regexPattern.test(state.url)) {
            if (route.queryParams['ltiSuccessLoginRequired']) {
                this.alertService.success('artemisApp.lti.ltiSuccessLoginRequired', { user: route.queryParams['ltiSuccessLoginRequired'] });
                return state.url.split('?')[0]; // Removes the query parameters from the url so this is only done once
            }
        }
        return state.url;
    }

    /**
     * Check whether user is logged in and has the required authorities.
     * @param {string[]}authorities List of required authorities.
     * @param {string} url Current url.
     * @return {Promise<boolean>} True if authorities are empty or null, False if user not logged in or does not have required authorities.
     */
    checkLogin(authorities: string[], url: string): Promise<boolean> {
        const accountService = this.accountService;
        return Promise.resolve(
            accountService.identity().then((account) => {
                if (!authorities || authorities.length === 0) {
                    return true;
                }

                if (account) {
                    return accountService.hasAnyAuthority(authorities).then((response) => {
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
