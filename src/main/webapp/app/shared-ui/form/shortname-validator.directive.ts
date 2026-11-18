import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

/**
 * Validator for the required regex pattern
 * @param regex Regex expression
 */
export function regexValidator(regex: RegExp): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
        const allowed = regex.test(control.value);
        return allowed ? null : { forbidden: { value: control.value } };
    };
}
