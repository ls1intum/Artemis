import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpClient, HttpParams } from '@angular/common/http';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-dynamic-registration',
    templateUrl: './lti13-dynamic-registration.component.html',
    imports: [TranslateDirective],
})
export class Lti13DynamicRegistrationComponent implements OnInit {
    private route = inject(ActivatedRoute);
    private http = inject(HttpClient);

    courseId!: number; // set in ngOnInit() from route params
    readonly isRegistering = signal(true);
    readonly registeredSuccessfully = signal(false);

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
            this.isRegistering.set(false);
            this.registeredSuccessfully.set(false);
            return;
        }

        let httpParams = new HttpParams().set('openid_configuration', openIdConfiguration);
        if (registrationToken) {
            httpParams = httpParams.set('registration_token', registrationToken);
        }

        this.http
            .post(`api/lti/admin/lti13/dynamic-registration`, null, { observe: 'response', params: httpParams })
            .subscribe({
                next: () => {
                    this.registeredSuccessfully.set(true);
                },
                error: () => {
                    this.registeredSuccessfully.set(false);
                },
            })
            .add(() => {
                this.isRegistering.set(false);
                this.notifyPlatformToClose(openIdConfiguration);
            });
    }

    /**
     * Sends the LTI close signal to the platform that opened this page.
     *
     * The target origin is derived from the platform's OpenID configuration URL rather than left as a wildcard.
     * Dynamic registration is always started by the platform, and that URL is the platform's own issuer endpoint, so
     * it identifies where this message belongs. The payload is only the close constant the specification defines and
     * says nothing about the course or the registration.
     *
     * @param openIdConfiguration the platform's OpenID configuration URL, as the platform passed it
     */
    private notifyPlatformToClose(openIdConfiguration: string): void {
        let platformOrigin: string;
        try {
            platformOrigin = new URL(openIdConfiguration).origin;
        } catch {
            // Without a parseable platform URL there is no origin to address. The registration has already been
            // persisted by the request above, so the only cost is that the window does not close by itself.
            return;
        }
        (window.opener || window.parent).postMessage({ subject: 'org.imsglobal.lti.close' }, platformOrigin);
    }
}
