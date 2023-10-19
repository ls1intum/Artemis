import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'jhi-select-exercise',
    templateUrl: './lti13-select-content.component.html',
})
export class Lti13SelectContentComponent implements OnInit {
    courseId: number;
    jwt: string;
    id: string;
    actionLink: string;

    constructor(private route: ActivatedRoute) {}

    /**
     * Initializes the component.
     * - Retrieves query parameters from the route snapshot.
     * - Sets the action link for the form.
     * - Automatically submits the form.
     */
    ngOnInit(): void {
        this.route.params.subscribe(() => {
            this.jwt = this.route.snapshot.queryParamMap.get('jwt') ?? '';
            this.id = this.route.snapshot.queryParamMap.get('id') ?? '';
            this.actionLink = this.route.snapshot.queryParamMap.get('deepLinkUri') ?? '';
            this.autoSubmitForm();
        });
    }

    /**
     * Automatically submits the form.
     * - Sets the action link for the form.
     * - Sets JWT and ID input fields.
     * - Submits the form.
     */
    autoSubmitForm(): void {
        const form = document.getElementById('deepLinkingForm') as HTMLFormElement;
        form.action = this.actionLink;
        console.log(this.actionLink);
        (<HTMLInputElement>document.getElementById('JWT'))!.value = this.jwt;
        (<HTMLInputElement>document.getElementById('id'))!.value = this.id;
        form.submit();
    }
}
