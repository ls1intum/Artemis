import { Directive, ElementRef, inject, input } from '@angular/core';
import { TumUiPopoverComponent } from './tum-ui-popover.component';

/**
 * Wires a trigger element to a {@link TumUiPopoverComponent}: click toggles the popover anchored to
 * the trigger, and the trigger reflects `aria-haspopup`/`aria-expanded` for accessibility.
 *
 * Usage: `<button [tumUiPopoverTrigger]="pop">Details</button> <tum-ui-popover #pop>...</tum-ui-popover>`
 */
@Directive({
    selector: '[tumUiPopoverTrigger]',
    host: {
        '(click)': 'toggle()',
        '[attr.aria-haspopup]': "'dialog'",
        '[attr.aria-expanded]': "popover().isOpen() ? 'true' : 'false'",
    },
})
export class TumUiPopoverTriggerDirective {
    private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
    readonly popover = input.required<TumUiPopoverComponent>({ alias: 'tumUiPopoverTrigger' });

    protected toggle(): void {
        this.popover().toggle(this.elementRef);
    }
}
