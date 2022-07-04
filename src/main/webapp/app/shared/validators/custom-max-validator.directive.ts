import { Directive, Input } from '@angular/core';
import { FormControl, NG_VALIDATORS, Validator } from '@angular/forms';

/**
 * Custom max validator as angular offers no such validator for template driven forms
 * See: https://github.com/angular/angular/issues/16352
 */
@Directive({
    // tslint:disable-next-line:directive-selector
    selector: '[customMax][formControlName],[customMax][formControl],[customMax][ngModel]',
    providers: [{ provide: NG_VALIDATORS, useExisting: CustomMaxDirective, multi: true }],
})
export class CustomMaxDirective implements Validator {
    @Input()
    customMax: number;

    validate(c: FormControl<number | undefined | null>): { [key: string]: any } | null {
        const v = c.value;
        if (v === undefined || v === null) {
            return null;
        }
        return +v > this.customMax ? { customMax: true } : null;
    }
}
