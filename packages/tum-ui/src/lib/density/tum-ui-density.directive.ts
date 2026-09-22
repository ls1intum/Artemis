import { Directive, input } from '@angular/core';

/** Applies the compact, 28px control density to a region, including projected controls. */
@Directive({
    selector: '[tumUiDensity]',
    host: { '[attr.data-tum-ui-density]': 'tumUiDensity()' },
})
export class TumUiDensityDirective {
    readonly tumUiDensity = input.required<'compact'>();
}
