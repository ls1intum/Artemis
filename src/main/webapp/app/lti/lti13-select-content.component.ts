import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, FormGroup } from '@angular/forms';

@Component({
    selector: 'jhi-select-exercise',
    templateUrl: './lti13-select-content.component.html',
})
export class Lti13SelectContentComponent implements OnInit {
    actionLink: string;
    form: FormGroup;
    isLinking = true;

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
        this.actionLink = this.route.snapshot.queryParamMap.get('deepLinkUri') ?? '';
        const jwt_token = this.route.snapshot.queryParamMap.get('jwt') ?? '';
        const id_token = this.route.snapshot.queryParamMap.get('id') ?? '';
        if (this.actionLink === '' || jwt_token === '' || id_token === '') {
            this.isLinking = false;
            return;
        }
        this.form.patchValue({
            JWT: jwt_token,
            id: id_token,
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
