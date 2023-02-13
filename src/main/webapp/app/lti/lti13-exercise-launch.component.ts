import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';

@Component({
    selector: 'jhi-lti-exercise-launch',
    templateUrl: './lti13-exercise-launch.component.html',
})
export class Lti13ExerciseLaunchComponent implements OnInit {
    isLaunching: boolean;

    constructor(private route: ActivatedRoute, private http: HttpClient) {
        this.isLaunching = true;
    }

    /**
     * perform an LTI launch with state and id_token query parameters
     */
    ngOnInit(): void {
        const state = this.route.snapshot.queryParamMap.get('state');
        const idToken = this.route.snapshot.queryParamMap.get('id_token');

        if (!state || !idToken) {
            console.error('Required parameter for LTI launch missing');
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

                    if (targetLinkUri) {
                        window.location.replace(targetLinkUri);
                        return;
                    } else {
                        this.isLaunching = false;
                        console.error('No LTI targetLinkUri received for a successful launch');
                    }
                },
                error: () => {
                    this.isLaunching = false;
                },
            });
    }
}
