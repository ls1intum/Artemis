import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';

@Component({
    selector: 'jhi-dynamic-registration',
    templateUrl: './lti13-dynamic-registration.component.html',
})
export class Lti13DeepLinkingComponent implements OnInit {
    courseId: number;
    isRegistering = true;
    registeredSuccessfully: boolean;

    constructor(
        private route: ActivatedRoute,
        private http: HttpClient,
    ) {}

    /**
     * perform LTI 13 deep linking
     */
    ngOnInit(): void {
        this.route.params.subscribe((params) => {
            this.courseId = Number(params['courseId']);
        });

        this.http.post('api/public/lti13/deep-linking', null);

        // this.http
        //     .post(`api/lti13/dynamic-registration/`, null, { observe: 'response', params: httpParams })
        //     .subscribe({
        //         next: () => {
        //             this.registeredSuccessfully = true;
        //         },
        //         error: () => {
        //             this.registeredSuccessfully = false;
        //         },
        //     })
        //     .add(() => {
        //         this.isRegistering = false;
        //         (window.opener || window.parent).postMessage({ subject: 'org.imsglobal.lti.close' }, '*');
        //     });
    }
}
