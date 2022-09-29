import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { LoginService } from 'app/core/login/login.service';
import { StateStorageService } from 'app/core/auth/state-storage.service';
import { Router } from '@angular/router';

@Component({
    selector: 'jhi-lti-exercise-launch',
    templateUrl: './lti-exercise-launch.component.html',
})
export class ArtemisLtiExerciseLaunchComponent implements OnInit {
    isLaunching: boolean;

    constructor(
        private route: ActivatedRoute,
        private http: HttpClient,
        private loginService: LoginService,
        private stateStorageService: StateStorageService,
        private router: Router,
    ) {
        this.isLaunching = true;
    }

    /**
     * perform a LTI launch with state and id_token query parameters
     */
    ngOnInit(): void {
        const state = this.route.snapshot.queryParamMap.get('state');
        const idToken = this.route.snapshot.queryParamMap.get('id_token');

        if (!state || !idToken) {
            console.error('Required parameter for LTI launch missing');
            this.isLaunching = false;
            return;
        }

        // 'state' was manually written into session storage by spring-security-lti13
        // because of that it needs to be manually retrieven from there
        const storedState = window.sessionStorage.getItem('state');

        if (storedState !== state) {
            console.error('LTI launch state missmatch');
            this.isLaunching = false;
            return;
        }

        const requestBody = new HttpParams().set('state', state).set('id_token', idToken);

        this.http
            .post(SERVER_API_URL + '/api/lti13/auth-login', requestBody.toString(), {
                headers: new HttpHeaders().set('Content-Type', 'application/x-www-form-urlencoded'),
            })
            .subscribe({
                next: (data) => {
                    const targetLinkUri = data['targetLinkUri'];
                    window.sessionStorage.removeItem('state');

                    if (targetLinkUri) {
                        window.location.href = targetLinkUri;
                        return;
                    }

                    this.isLaunching = false;
                    console.error('No LTI targetLinkUri received for a successful launch');
                },
                error: (err) => {
                    // anonymous LTI launches are not permitted
                    if (err.status === 401) {
                        this.stateStorageService.storeUrl(this.router.routerState.snapshot.url);
                        this.loginService.logout(false);
                        return;
                    }

                    window.sessionStorage.removeItem('state');
                    this.isLaunching = false;
                },
            });
    }
}
