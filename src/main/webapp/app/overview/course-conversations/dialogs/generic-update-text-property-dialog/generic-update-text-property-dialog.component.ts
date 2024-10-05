import { Component, Input, inject } from '@angular/core';
import { FormGroup, Validators } from '@angular/forms';
import { FormBuilder } from '@angular/forms';
import { AbstractDialogComponent } from 'app/overview/course-conversations/dialogs/abstract-dialog.component';

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
export class GenericUpdateTextPropertyDialogComponent extends AbstractDialogComponent {
    private fb = inject(FormBuilder);

    @Input() propertyName: string;
    @Input() isRequired = false;
    @Input() regexPattern: RegExp | undefined;
    @Input() maxPropertyLength: number;
    @Input() initialValue: string | undefined;
    @Input() translationKeys: GenericUpdateTextPropertyTranslationKeys;
    form: FormGroup;

    initialize() {
        super.initialize(['propertyName', 'maxPropertyLength', 'translationKeys']);
        if (this.isInitialized) {
            this.initializeForm();
        }
    }

    get isSubmitPossible() {
        return !this.form.invalid;
    }

    get control() {
        return this.form.get(this.propertyName);
    }

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
        this.dismiss();
    }

    submitForm() {
        this.close(this.control!.value);
    }
}
