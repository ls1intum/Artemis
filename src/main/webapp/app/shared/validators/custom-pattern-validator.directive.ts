import { Directive } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, Validator } from '@angular/forms';

/**
 * Custom validator for patterns (RegEx)
 */
@Directive({
    // tslint:disable-next-line:directive-selector
    selector: '[validPattern][ngModel],[validPattern][formControl]',
    providers: [{ provide: NG_VALIDATORS, useExisting: CustomPatternValidatorDirective, multi: true }],
})
export class CustomPatternValidatorDirective implements Validator {
    validate(control: AbstractControl): { [key: string]: any } | null {
        if (control == undefined) {
            return null;
        }
        try {
            // tslint:disable-next-line:no-unused-expression-chai
            new RegExp(control.value);
            return null;
        } catch (e) {
            return { validPattern: true };
        }
    }
}
