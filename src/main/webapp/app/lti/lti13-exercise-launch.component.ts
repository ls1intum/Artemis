import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-lti-exercise-launch',
    templateUrl: './lti13-exercise-launch.component.html',
})
export class Lti13ExerciseLaunchComponent implements OnInit {
    isLaunching: boolean;

    constructor(
        private route: ActivatedRoute,
        private http: HttpClient,
        private alertService: AlertService,
        private accountService: AccountService,
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
        const username = this.route.snapshot.queryParamMap.get('authenticatedUser');

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

        let requestBody = new HttpParams().set('state', state).set('id_token', idToken);
        if (username) requestBody = requestBody.set('authenticatedUser', username);

        this.http
            .post('api/public/lti13/auth-login', requestBody.toString(), {
                headers: new HttpHeaders().set('Content-Type', 'application/x-www-form-urlencoded'),
            })
            .subscribe({
                next: (data) => {
                    const targetLinkUri = data['targetLinkUri'];
                    window.sessionStorage.removeItem('state');

                    if (targetLinkUri) {
                        window.location.replace(targetLinkUri);
                    } else {
                        this.isLaunching = false;
                        console.error('No LTI targetLinkUri received for a successful launch');
                    }
                },
                error: (error) => {
                    if (error.status === 401) {
                        if (this.accountService.isAuthenticated() && this.accountService.userIdentity?.login === username) {
                            this.sendRequest();
                        } else {
                            // Subscribe to the authentication state to know when the user logs in
                            this.accountService.getAuthenticationState().subscribe((user) => {
                                const username = this.route.snapshot.queryParamMap.get('authenticatedUser');
                                if (username) {
                                    this.alertService.success('artemisApp.lti.ltiSuccessLoginRequired', { user: username });
                                    this.accountService.setPrefilledUsername(username);
                                }
                                window.location.replace('');
                                if (user) {
                                    // resend request when user logs in again
                                    this.sendRequest();
                                }
                            });
                        }
                    } else {
                        window.sessionStorage.removeItem('state');
                        this.isLaunching = false;
                    }
                },
            });
    }
}
