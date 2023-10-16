import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-deep-linking',
    templateUrl: './lti13-deep-linking.component.html',
})
export class Lti13DeepLinkingComponent implements OnInit {
    courseId: number;
    registeredSuccessfully: boolean;
    jwt: string;
    id: string;
    actionLink: string;
    response: string;

    constructor(private route: ActivatedRoute) {}

    /**
     * perform LTI 13 deep linking
     */
    ngOnInit(): void {
        this.route.params.subscribe(() => {
            this.jwt = this.route.snapshot.queryParamMap.get('jwt') ?? '';
            this.id = this.route.snapshot.queryParamMap.get('id') ?? '';
            this.actionLink = this.route.snapshot.queryParamMap.get('deepLinkUri') ?? '';
            this.autoSubmitForm();
        });
    }

    autoSubmitForm(): void {
        const form = document.getElementById('deepLinkingForm') as HTMLFormElement;
        form.action = this.actionLink;
        console.log(this.actionLink);
        (<HTMLInputElement>document.getElementById('JWT'))!.value = this.jwt;
        (<HTMLInputElement>document.getElementById('id'))!.value = this.id;
        form.submit();
    }
}
