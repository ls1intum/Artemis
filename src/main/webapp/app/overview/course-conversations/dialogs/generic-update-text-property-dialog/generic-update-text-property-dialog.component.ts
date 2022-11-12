import { Component, Input } from '@angular/core';
import { FormGroup, Validators } from '@angular/forms';
import { FormBuilder } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

export interface GenericUpdateTextPropertyTranslationKeys {
    labelKey: string;
    titleKey: string;

    helpKey: string;

    maxLengthErrorKey: string;

    requiredErrorKey: string;

    regexErrorKey: string;
}

@Component({
    selector: 'jhi-generic-update-text-property-dialog',
    templateUrl: './generic-update-text-property-dialog.component.html',
})
export class GenericUpdateTextPropertyDialog {
    @Input()
    propertyName: string;

    @Input()
    isRequired = false;

    @Input()
    regexPattern: RegExp | undefined;

    @Input()
    maxPropertyLength: number;

    @Input()
    initialValue: string | undefined;

    @Input()
    translationKeys: GenericUpdateTextPropertyTranslationKeys;
    form: FormGroup;

    isInitialized = false;

    initialize() {
        if (!this.propertyName || !this.maxPropertyLength || !this.translationKeys) {
            console.error('Error: Dialog not fully configured');
        } else {
            this.isInitialized = true;
            this.initializeForm();
        }
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    get control() {
        return this.form.get(this.propertyName);
    }
    constructor(private fb: FormBuilder, private activeModal: NgbActiveModal) {}
    private initializeForm() {
        if (this.form) {
            return;
        }

        const validators = [];
        if (this.isRequired) {
            validators.push(Validators.required);
        }
        if (this.regexPattern) {
            validators.push(Validators.pattern(this.regexPattern));
        }
        if (this.maxPropertyLength) {
            validators.push(Validators.maxLength(this.maxPropertyLength));
        }

        this.form = this.fb.group({
            [this.propertyName]: [this.initialValue, validators],
        });
    }

    clear() {
        this.activeModal.dismiss();
    }

    submitForm() {
        this.activeModal.close(this.control!.value);
    }
}
