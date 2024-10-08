import { Component, ElementRef, NgZone, OnInit, SecurityContext, ViewChild, inject } from '@angular/core';
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
    private route = inject(ActivatedRoute);
    private sanitizer = inject(DomSanitizer);
    private zone = inject(NgZone);

    jwt: string;
    id: string;
    actionLink: string;
    isLinking = true;

    @ViewChild('deepLinkingForm', { static: false })
    deepLinkingForm?: ElementRef;

    /**
     * Initializes the component.
     * - Retrieves query parameters from the route snapshot.
     * - Sets the action link for the form.
     * - Automatically submits the form.
     */
    ngOnInit(): void {
        this.route.params.subscribe(() => {
            this.updateFormValues();

            // postpone auto-submit until after view updates are completed
            // if not jwt and id is not submitted correctly
            if (this.jwt && this.id) {
                this.zone.runOutsideAngular(() => {
                    setTimeout(() => this.autoSubmitForm());
                });
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
        const form = this.deepLinkingForm?.nativeElement;
        if (form) {
            form.action = this.actionLink;
            form.submit();
        }
    }
}
