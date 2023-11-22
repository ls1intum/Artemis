import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { captureException } from '@sentry/angular-ivy';
import { SessionStorageService } from 'ngx-webstorage';

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

        // 'state' was manually written into session storage by spring-security-lti13
        // because of that it needs to be manually retrieved from there
        const storedState = window.sessionStorage.getItem('state');

        if (storedState !== state) {
            console.error('LTI launch state mismatch');
            this.isLaunching = false;
            return;
        }

        const requestBody = new HttpParams().set('state', state).set('id_token', idToken);

        this.http
            .post('api/public/lti13/auth-login', requestBody.toString(), {
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
        this.accountService.identity().then((user) => {
            if (user) {
                this.redirectUserToTargetLink(error);
            } else {
                this.redirectUserToLoginThenTargetLink(error);
            }
        });
    }

    redirectUserToTargetLink(error: any): void {
        const ltiIdToken = error.headers.get('ltiIdToken');
        const clientRegistrationId = error.headers.get('clientRegistrationId');

        this.storeLtiSessionData(ltiIdToken, clientRegistrationId);

        // Redirect to target link since the user is already logged in
        window.location.replace(error.headers.get('TargetLinkUri').toString());
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

    handleLtiLaunchSuccess(data: NonNullable<unknown>): void {
        const targetLinkUri = data['targetLinkUri'];
        const ltiIdToken = data['ltiIdToken'];
        const clientRegistrationId = data['clientRegistrationId'];

        window.sessionStorage.removeItem('state');
        this.storeLtiSessionData(ltiIdToken, clientRegistrationId);

        if (targetLinkUri) {
            window.location.replace(targetLinkUri);
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
}
