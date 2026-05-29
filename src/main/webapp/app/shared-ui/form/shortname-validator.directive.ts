import { AbstractControl, ValidatorFn } from '@angular/forms';

/**
 * Validator for the required regex pattern
 * @param regex Regex expression
 */
export function regexValidator(regex: RegExp): ValidatorFn {
    return (control: AbstractControl): { [key: string]: any } | null => {
        const allowed = regex.test(control.value);
        return allowed ? null : { forbidden: { value: control.value } };
    };
}
