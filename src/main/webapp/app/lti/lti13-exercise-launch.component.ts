import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { captureException } from '@sentry/angular';
import { SessionStorageService } from 'ngx-webstorage';
import { LtiService } from 'app/shared/service/lti.service';

type LtiLaunchResponse = {
    targetLinkUri: string;
    ltiIdToken: string;
    clientRegistrationId: string;
};

@Component({
    selector: 'jhi-lti-exercise-launch',
    templateUrl: './lti13-exercise-launch.component.html',
})
export class Lti13ExerciseLaunchComponent implements OnInit {
    isLaunching: boolean;

    constructor(
        private route: ActivatedRoute,
        private http: HttpClient,
        private accountService: AccountService,
        private router: Router,
        private sessionStorageService: SessionStorageService,
        private ltiService: LtiService,
    ) {
        this.isLaunching = true;
    }

    /**
     * perform an LTI launch with state and id_token query parameters
     */
    ngOnInit(): void {
        this.sendRequest();
    }

    sendRequest(): void {
        const state = this.route.snapshot.queryParamMap.get('state');
        const idToken = this.route.snapshot.queryParamMap.get('id_token');

        if (!state || !idToken) {
            console.error('Required parameter for LTI launch missing');
            this.isLaunching = false;
            return;
        }

        const requestBody = new HttpParams().set('state', state).set('id_token', idToken);

        this.http
            .post<LtiLaunchResponse>('api/public/lti13/auth-login', requestBody.toString(), {
                headers: new HttpHeaders().set('Content-Type', 'application/x-www-form-urlencoded'),
            })
            .subscribe({
                next: (data) => {
                    this.handleLtiLaunchSuccess(data);
                },
                error: (error) => {
                    if (error.status === 401) {
                        this.authenticateUserThenRedirect(error);
                    } else {
                        this.handleLtiLaunchError();
                    }
                },
            });
    }

    authenticateUserThenRedirect(error: any): void {
        const loginName = error.headers.get('ltiSuccessLoginRequired');
        this.accountService.identity().then((user) => {
            if (user) {
                this.redirectUserToTargetLink(error);
            } else {
                if (loginName) {
                    this.accountService.setPrefilledUsername(loginName);
                }
                this.redirectUserToLoginThenTargetLink(error);
            }
        });
    }

    redirectUserToTargetLink(data: any): void {
        const ltiIdToken = data.error['ltiIdToken'];
        const clientRegistrationId = data.error['clientRegistrationId'];

        this.storeLtiSessionData(ltiIdToken, clientRegistrationId);

        // Redirect to target link since the user is already logged in
        this.replaceWindowLocationWrapper(data.error['targetLinkUri'].toString());
    }

    redirectUserToLoginThenTargetLink(error: any): void {
        // Redirect the user to the login page
        this.router.navigate(['/']).then(() => {
            // After navigating to the login page, set up a listener for when the user logs in
            this.accountService.getAuthenticationState().subscribe((user) => {
                if (user) {
                    this.redirectUserToTargetLink(error);
                }
            });
        });
    }

    handleLtiLaunchSuccess(data: LtiLaunchResponse): void {
        const targetLinkUri = data.targetLinkUri;
        const ltiIdToken = data.ltiIdToken;
        const clientRegistrationId = data.clientRegistrationId;

        window.sessionStorage.removeItem('state');
        this.storeLtiSessionData(ltiIdToken, clientRegistrationId);

        if (targetLinkUri) {
            this.replaceWindowLocationWrapper(targetLinkUri);
        } else {
            this.isLaunching = false;
            console.error('No LTI targetLinkUri received for a successful launch');
        }
    }

    handleLtiLaunchError(): void {
        window.sessionStorage.removeItem('state');
        this.isLaunching = false;
    }

    storeLtiSessionData(ltiIdToken: string, clientRegistrationId: string): void {
        if (!ltiIdToken) {
            captureException(new Error('LTI ID token required to store session data.'));
            return;
        }

        if (!clientRegistrationId) {
            captureException(new Error('LTI client registration ID required to store session data.'));
            return;
        }

        try {
            this.sessionStorageService.store('ltiIdToken', ltiIdToken);
            this.sessionStorageService.store('clientRegistrationId', clientRegistrationId);
        } catch (error) {
            console.error('Failed to store session data:', error);
        }
    }

    replaceWindowLocationWrapper(url: string): void {
        this.ltiService.setLti(true);
        window.location.replace(url);
    }
}
