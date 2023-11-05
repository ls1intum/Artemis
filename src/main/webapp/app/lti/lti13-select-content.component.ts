import { Component, OnInit, SecurityContext } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { DomSanitizer } from '@angular/platform-browser';

/**
 * Component responsible for sending deep linking content.
 * It reads the necessary parameters from the route, sanitizes return URL,
 * and automatically submits a form with the relevant data.
 * According to LTI documentation auto submit form must be used.
 */
@Component({
    selector: 'jhi-select-exercise',
    templateUrl: './lti13-select-content.component.html',
})
export class Lti13SelectContentComponent implements OnInit {
    jwt: string;
    id: string;
    actionLink: string;
    isLinking = true;

    constructor(
        private route: ActivatedRoute,
        private sanitizer: DomSanitizer,
    ) {}

    /**
     * Initializes the component.
     * - Retrieves query parameters from the route snapshot.
     * - Sets the action link for the form.
     * - Automatically submits the form.
     */
    ngOnInit(): void {
        this.route.params.subscribe(() => {
            this.updateFormValues();
            if (this.isLinking) {
                this.autoSubmitForm();
            }
        });
    }

    /**
     * Updates the form values with query parameters
     * - Retrieves query parameters from the route snapshot.
     */
    updateFormValues(): void {
        const deepLinkUri = this.route.snapshot.queryParamMap.get('deepLinkUri') ?? '';
        this.actionLink = this.sanitizer.sanitize(SecurityContext.URL, deepLinkUri) || '';
        this.jwt = this.route.snapshot.queryParamMap.get('jwt') ?? '';
        this.id = this.route.snapshot.queryParamMap.get('id') ?? '';
        if (this.actionLink === '' || this.jwt === '' || this.id === '') {
            this.isLinking = false;
            return;
        }
    }

    /**
     * Automatically submits the form.
     * - Sets the action link for the form.
     * - Submits the form.
     */
    autoSubmitForm(): void {
        const form = document.getElementById('deepLinkingForm') as HTMLFormElement;
        form.action = this.actionLink;
        (<HTMLInputElement>document.getElementById('JWT'))!.value = this.jwt;
        (<HTMLInputElement>document.getElementById('id'))!.value = this.id;
        form.submit();
    }
}
