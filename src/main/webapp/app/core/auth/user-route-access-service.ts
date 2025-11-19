import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivate, Router, RouterStateSnapshot } from '@angular/router';
import { AccountService } from 'app/core/auth/account.service';
import { Authority } from 'app/shared/constants/authority.constants';
import { AlertService } from 'app/shared/service/alert.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';

@Injectable({ providedIn: 'root' })
export class UserRouteAccessService implements CanActivate {
    private router = inject(Router);
    private accountService = inject(AccountService);
    private alertService = inject(AlertService);
    private sessionStorageService = inject(SessionStorageService);

    /**
     * Check if the client can activate a route.
     * @param route The ActivatedRouteSnapshot of the route to activate.
     * @param state The current RouterStateSnapshot.
     * @return True if user is logged in, false otherwise.
     */
    canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot): boolean | Promise<boolean> {
        const ltiRedirectUrl = this.handleLTIRedirect(route, state);
        const urlToStore = ltiRedirectUrl ?? state.url;

        let authorities = route.data['authorities'];

        // For programming exercise template and solution participations editors shall be allowed to view the submissions, but not for other submissions.
        // To ensure this behavior the query parameter of the route needs to be considered and the Editor authority needs to be added subsequently within the
        // canActivate check, as it can not be allowed directly within the corresponding router since this would allow access to all submissions.
        if (
            (route.routeConfig?.path === ':courseId/programming-exercises/:exerciseId/participations/:participationId/submissions' ||
                route.routeConfig?.path === ':examId/exercise-groups/:exerciseGroupId/programming-exercises/:exerciseId/participations/:participationId') &&
            route.queryParams['isTmpOrSolutionProgrParticipation'] === 'true' &&
            authorities
        ) {
            /** Create a new array instead of mutating the existing as routes are defined as readonly {@link IS_AT_LEAST_INSTRUCTOR} */
            authorities = [...authorities, Authority.EDITOR];
        }
        // We need to call the checkLogin / and so the accountService.identity() function, to ensure,
        // that the client has an account too, if they already logged in by the server.
        // This could happen on a page refresh.
        return this.checkLogin(authorities, urlToStore);
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
                const username = route.queryParams['ltiSuccessLoginRequired'];
                this.alertService.success('artemisApp.lti.ltiSuccessLoginRequired', { user: username });
                this.accountService.setPrefilledUsername(username);
                return state.url.split('?')[0]; // Removes the query parameters from the url so this is only done once
            }
        }
        return state.url;
    }

    /**
     * Check whether user is logged in and has the required authorities.
     * @param authorities List of required authorities.
     * @param url Current url.
     * @return True if authorities are empty or null, False if user not logged in or does not have required authorities.
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
                        return !!response;
                    });
                }

                this.sessionStorageService.store('previousUrl', url);
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
