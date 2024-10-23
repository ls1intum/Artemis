import { Directive, input } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, Validator } from '@angular/forms';

/**
 * Custom validator for an array of excluded values for an input element
 *
 * Adds the 'disallowedValue' error key (= true) to the control if the value is in the disallowedValues array
 */
@Directive({
    // eslint-disable-next-line @angular-eslint/directive-selector
    selector: '[notIncludedIn][ngModel],[notIncludedIn][formControl]',
    providers: [{ provide: NG_VALIDATORS, useExisting: CustomNotIncludedInValidatorDirective, multi: true }],
    standalone: true,
})
export class CustomNotIncludedInValidatorDirective implements Validator {
    disallowedValues = input.required<Set<unknown>>();

    validate(control: AbstractControl): { [key: string]: any } | null {
        if (control == undefined) {
            return null;
        }

        const isValueAlreadyTaken = this.disallowedValues().has(control.value);
        if (isValueAlreadyTaken) {
            return { disallowedValue: true };
        }

        return null;
    }
}
