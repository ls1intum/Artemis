import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpClient, HttpParams } from '@angular/common/http';

@Component({
    selector: 'jhi-dynamic-registration',
    templateUrl: './lti13-dynamic-registration.component.html',
})
export class Lti13DynamicRegistrationComponent implements OnInit {
    courseId: number;
    isRegistering = true;
    registeredSuccessfully: boolean;

    constructor(private route: ActivatedRoute, private http: HttpClient) {}

    /**
     * perform LTI 13 dynamic registration
     */
    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
        });

        const openIdConfiguration = this.route.snapshot.queryParamMap.get('openid_configuration');
        const registrationToken = this.route.snapshot.queryParamMap.get('registration_token');

        if (!openIdConfiguration) {
            console.error('Required parameter for LTI dynamic registration missing');
            this.isRegistering = false;
            this.registeredSuccessfully = false;
            return;
        }

        let httpParams = new HttpParams().set('openid_configuration', openIdConfiguration);
        if (registrationToken) {
            httpParams = httpParams.set('registration_token', registrationToken);
        }

        this.http
            .get(`${SERVER_API_URL}/api/lti13/dynamic-registration/${this.courseId}`, { observe: 'response', params: httpParams })
            .subscribe({
                next: () => {
                    this.registeredSuccessfully = true;
                },
                error: () => {
                    this.registeredSuccessfully = false;
                },
            })
            .add(() => {
                this.isRegistering = false;
                (window.opener || window.parent).postMessage('org.imsglobal.lti.close', '*');
            });
    }
}
