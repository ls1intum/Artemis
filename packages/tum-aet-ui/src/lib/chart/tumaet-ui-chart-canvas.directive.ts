import { Directive, input } from '@angular/core';

/** An unnamed chart is decorative; a label or visible heading makes its SVG available to assistive technology. */
@Directive({
    selector: 'svg[tumAetUiChartCanvas]',
    host: {
        class: 'tumaet-ui-chart-canvas',
        role: 'img',
        '[attr.aria-label]': 'ariaLabel()',
        '[attr.aria-labelledby]': 'ariaLabelledBy()',
        '[attr.aria-hidden]': 'ariaLabel() || ariaLabelledBy() ? undefined : true',
    },
})
export class TumAetUiChartCanvasDirective {
    readonly ariaLabel = input<string>();
    readonly ariaLabelledBy = input<string>();
}
