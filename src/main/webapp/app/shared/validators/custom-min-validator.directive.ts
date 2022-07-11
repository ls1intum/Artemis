import { Directive, Input } from '@angular/core';
import { FormControl, NG_VALIDATORS, Validator } from '@angular/forms';

/**
 * Custom min validator as angular offers no such validator for template driven forms
 * See: https://github.com/angular/angular/issues/16352
 */
@Directive({
    // tslint:disable-next-line:directive-selector
    selector: '[customMin][formControlName],[customMin][formControl],[customMin][ngModel]',
    providers: [{ provide: NG_VALIDATORS, useExisting: CustomMinDirective, multi: true }],
})
export class CustomMinDirective implements Validator {
    @Input()
    customMin: number;

    validate(c: FormControl<number | undefined | null>): { [key: string]: any } | null {
        const v = c.value;
        if (v === undefined || v === null) {
            return null;
        }
        return +v < this.customMin ? { customMin: true } : null;
    }
}
