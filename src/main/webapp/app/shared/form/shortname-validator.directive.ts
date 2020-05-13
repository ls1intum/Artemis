import { AbstractControl, ValidatorFn } from '@angular/forms';

/**
 * Validator for the required regex pattern of the short name
 * @param {RegExp} regex
 * @returns ValidatorFn
 */
export function regexValidator(regex: RegExp): ValidatorFn {
    return (control: AbstractControl): { [key: string]: any } | null => {
        const allowed = regex.test(control.value);
        return allowed ? null : { forbidden: { value: control.value } };
    };
}
