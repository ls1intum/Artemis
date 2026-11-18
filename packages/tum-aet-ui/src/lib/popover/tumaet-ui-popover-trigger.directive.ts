import { Directive, ElementRef, inject, input } from '@angular/core';
import { TumAetUiPopoverComponent } from './tumaet-ui-popover.component';

/**
 * Wires a trigger element to a {@link TumAetUiPopoverComponent}: click toggles the popover anchored to
 * the trigger, and the trigger reflects `aria-haspopup`/`aria-expanded` for accessibility.
 *
 * Usage: `<button [tumAetUiPopoverTrigger]="pop">Details</button> <tumaet-ui-popover #pop>...</tumaet-ui-popover>`
 */
@Directive({
    selector: '[tumAetUiPopoverTrigger]',
    host: {
        '(click)': 'toggle()',
        '[attr.aria-haspopup]': "'dialog'",
        '[attr.aria-expanded]': "popover().isOpen() ? 'true' : 'false'",
    },
})
export class TumAetUiPopoverTriggerDirective {
    private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
    readonly popover = input.required<TumAetUiPopoverComponent>({ alias: 'tumAetUiPopoverTrigger' });

    protected toggle(): void {
        this.popover().toggle(this.elementRef);
    }
}
