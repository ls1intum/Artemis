import { Directive, input } from '@angular/core';

/** Inherited control geometry; default resets a compact region without changing control size inputs. */
@Directive({
    selector: '[tumUiDensity]',
    host: { '[attr.data-tum-ui-density]': 'tumUiDensity()' },
})
export class TumUiDensityDirective {
    readonly tumUiDensity = input.required<'default' | 'compact'>();
}
