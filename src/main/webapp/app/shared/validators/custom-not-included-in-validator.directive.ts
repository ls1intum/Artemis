import { Directive, input } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, Validator } from '@angular/forms';

/**
 * Custom validator for an array of excluded values for an input element
 */
@Directive({
    // eslint-disable-next-line @angular-eslint/directive-selector
    selector: '[notIncludedIn][ngModel],[notIncludedIn][formControl]',
    providers: [{ provide: NG_VALIDATORS, useExisting: CustomNotIncludedInValidatorDirective, multi: true }],
    standalone: true,
})
export class CustomNotIncludedInValidatorDirective implements Validator {
    disallowedValues = input.required<any[]>();

    validate(control: AbstractControl): { [key: string]: any } | null {
        if (control == undefined) {
            return null;
        }

        const isValueAlreadyTaken = this.disallowedValues().includes(control.value);
        if (isValueAlreadyTaken) {
            return { disallowedValue: true };
        }

        return null;
    }
}
