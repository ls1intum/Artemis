import { Directive, input } from '@angular/core';
import { FormControl, NG_VALIDATORS, Validator } from '@angular/forms';

/**
 * Custom max length validator for template-driven forms.
 */
@Directive({
    selector: '[customMaxLength][formControlName],[customMaxLength][formControl],[customMaxLength][ngModel]',
    providers: [{ provide: NG_VALIDATORS, useExisting: CustomMaxLengthDirective, multi: true }],
})
export class CustomMaxLengthDirective implements Validator {
    customMaxLength = input.required<number>();

    validate(c: FormControl<string | undefined | null>): { [key: string]: any } | null {
        const value = c.value;
        if (value === undefined || value === null) {
            return null;
        }
        return value.length > this.customMaxLength() ? { customMaxLength: true } : null;
    }
}
