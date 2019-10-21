import { AbstractControl, ValidatorFn } from '@angular/forms';

export function regexValidator(regex: RegExp): ValidatorFn {
    return (control: AbstractControl): { [key: string]: any } | null => {
        const allowed = regex.test(control.value);
        return allowed ? null : { forbidden: { value: control.value } };
    };
}
