import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Validator that requires the control value to be a whole number.
 * Empty values (null, undefined, empty string) are considered valid so that this can be combined with a separate required validator.
 */
export function integerValidator(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
        const value = control.value;
        if (value === null || value === undefined || value === '') {
            return null;
        }
        return Number.isInteger(value) ? null : { notInteger: { value } };
    };
}
