import { Directive, ElementRef, computed, inject } from '@angular/core';
import { TumUiTooltipDirective } from '../tooltip/tum-ui-tooltip.directive';

/**
 * Soft-disables a button or link and says why.
 *
 * A natively `disabled` control is mute: it receives no pointer events, so it can carry no tooltip, and it drops out of
 * the tab order, so a keyboard user cannot even discover it. This directive keeps the control hoverable and focusable,
 * marks it `aria-disabled` (which the button styles render like `disabled`), shows the reason as a tooltip on hover and
 * focus, and swallows activation before any other handler on the element runs - a native `click`, and therefore Enter
 * and Space on a button or link, never reaches `(click)` or `routerLink`. An empty reason re-enables the control.
 *
 * ```html
 * <button tumUiButton [tumUiDisabledReason]="saveBlockedReason()" (click)="save()">Save</button>
 * ```
 *
 * Menu items are the one place not to use it: `[tumUiMenuItem]` activates through the CDK menu's own key handling, so
 * set its `disabled` input and render the reason as a second line of the item instead.
 */
@Directive({
    selector: '[tumUiDisabledReason]',
    hostDirectives: [{ directive: TumUiTooltipDirective, inputs: ['tumUiTooltip: tumUiDisabledReason', 'tumUiTooltipPlacement'] }],
    host: {
        '[attr.aria-disabled]': "reason() ? 'true' : null",
    },
})
export class TumUiDisabledReasonDirective {
    private readonly tooltip = inject(TumUiTooltipDirective);
    private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);

    /** The tooltip receives the reason through the host-directive input mapping, so it is the single source of it. */
    protected readonly reason = computed(() => this.tooltip.content());

    constructor() {
        // Registered at the capture phase so it runs before every bubbling listener on the same element, including the
        // ones Angular attaches for `(click)` and `routerLink`.
        this.elementRef.nativeElement.addEventListener(
            'click',
            (event) => {
                if (this.reason()) {
                    event.preventDefault();
                    event.stopImmediatePropagation();
                }
            },
            { capture: true },
        );
    }
}
