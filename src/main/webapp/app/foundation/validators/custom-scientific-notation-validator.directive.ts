import { Directive, ElementRef, inject } from '@angular/core';
import { AbstractControl, NG_VALIDATORS, ValidationErrors, Validator } from '@angular/forms';

/**
 * Matches a plain, optionally signed, optionally decimal number (e.g. "1", "-1", "1.5").
 * Rejects scientific notation (e.g. "1e-30", "1E5") and other non-plain-decimal strings,
 * which native `<input type="number">` elements otherwise silently accept as valid.
 */
const PLAIN_DECIMAL_PATTERN = /^[+-]?\d*\.?\d*$/;

/**
 * Custom validator that rejects scientific notation on native number inputs.
 *
 * Native `<input type="number">` parses "1e-30" into a valid, in-range float, so `min`/`max`
 * validators never see anything wrong with it - they only ever see the already-parsed number.
 * This directive instead reads the raw string straight from the input element, which is the
 * only place the original, un-parsed format is still available.
 *
 * Adds the 'scientificNotation' error key (= true) to the control if the entered value is not
 * a plain decimal number.
 */
@Directive({
    selector: 'input[type=number][noScientificNotation][ngModel], input[type=number][noScientificNotation][formControl]',
    providers: [{ provide: NG_VALIDATORS, useExisting: CustomScientificNotationValidatorDirective, multi: true }],
})
export class CustomScientificNotationValidatorDirective implements Validator {
    private readonly elementRef = inject(ElementRef<HTMLInputElement>);

    validate(control: AbstractControl): ValidationErrors | null {
        if (control == undefined) {
            return null;
        }

        const rawValue = this.elementRef.nativeElement.value;
        if (rawValue !== '' && !PLAIN_DECIMAL_PATTERN.test(rawValue)) {
            return { scientificNotation: true };
        }

        return null;
    }
}
