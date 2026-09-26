import { Directive, ElementRef, inject } from '@angular/core';
import { NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';

/**
 * Custom validator that rejects numbers written in scientific notation, e.g. 1e-3
 *
 * Adds the 'scientificNotation' error key (= true) to the control. The raw text of the input element is checked instead of the
 * control value, because a number input has already parsed 1e-3 into 0.001 by the time the control value is set.
 */
@Directive({
    selector: 'input[type=number][noScientificNotation][ngModel],input[type=number][noScientificNotation][formControl]',
    providers: [{ provide: NG_VALIDATORS, useExisting: CustomNoScientificNotationValidatorDirective, multi: true }],
})
export class CustomNoScientificNotationValidatorDirective implements Validator {
    private readonly elementRef = inject<ElementRef<HTMLInputElement>>(ElementRef);

    validate(): ValidationErrors | null {
        if (/e/i.test(this.elementRef.nativeElement.value)) {
            return { scientificNotation: true };
        }

        return null;
    }
}
