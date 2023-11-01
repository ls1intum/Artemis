import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, FormGroup } from '@angular/forms';

@Component({
    selector: 'jhi-select-exercise',
    templateUrl: './lti13-select-content.component.html',
})
export class Lti13SelectContentComponent implements OnInit {
    courseId: number;
    jwt: string;
    id: string;
    actionLink: string;
    form: FormGroup;

    constructor(
        private route: ActivatedRoute,
        private formBuilder: FormBuilder,
    ) {
        this.form = this.formBuilder.group({
            JWT: [''],
            id: [''],
        });
    }

    /**
     * Initializes the component.
     * - Retrieves query parameters from the route snapshot.
     * - Sets the action link for the form.
     * - Automatically submits the form.
     */
    ngOnInit(): void {
        this.route.params.subscribe(() => {
            this.updateFormValues();
            this.autoSubmitForm();
        });
    }

    /**
     * Updates the form values with query parameters
     * - Retrieves query parameters from the route snapshot.
     */
    updateFormValues(): void {
        this.actionLink = this.route.snapshot.queryParamMap.get('deepLinkUri') ?? '';
        this.form.patchValue({
            JWT: this.route.snapshot.queryParamMap.get('jwt') ?? '',
            id: this.route.snapshot.queryParamMap.get('id') ?? '',
        });
    }

    /**
     * Automatically submits the form.
     * - Sets the action link for the form.
     * - Submits the form.
     */
    autoSubmitForm(): void {
        const form = document.getElementById('deepLinkingForm') as HTMLFormElement;
        form.action = this.actionLink;
        form.submit();
    }
}
