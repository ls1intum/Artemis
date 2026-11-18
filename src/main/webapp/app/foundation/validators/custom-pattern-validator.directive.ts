import { Directive } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';

/**
 * Custom validator for patterns (RegEx)
 */
@Directive({
    selector: '[validPattern][ngModel],[validPattern][formControl]',
    providers: [{ provide: NG_VALIDATORS, useExisting: CustomPatternValidatorDirective, multi: true }],
})
export class CustomPatternValidatorDirective implements Validator {
    validate(control: AbstractControl): ValidationErrors | null {
        if (control == undefined) {
            return null;
        }
        try {
            new RegExp(control.value);
            return null;
        } catch (e) {
            return { validPattern: true };
        }
    }
}
