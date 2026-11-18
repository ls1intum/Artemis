import { Directive, ElementRef, inject, input } from '@angular/core';
import { TumAetUiPopoverComponent } from './tumaet-ui-popover.component';

/**
 * Wires a trigger element to a {@link TumAetUiPopoverComponent}, anchors the popover to the trigger, and reflects
 * `aria-haspopup`/`aria-expanded` on it for accessibility.
 *
 * What opens the popover follows its `openOn` input. By default a click toggles it. With `openOn="hover"` it opens
 * while the pointer or the keyboard focus is on the trigger, a click is left to the trigger's own handler, and Tab
 * moves from the trigger into the open panel. Opened by touch, it stays until it is dismissed.
 *
 * Usage: `<button [tumAetUiPopoverTrigger]="pop">Details</button> <tumaet-ui-popover #pop>...</tumaet-ui-popover>`
 */
@Directive({
    selector: '[tumAetUiPopoverTrigger]',
    host: {
        '(click)': 'onClick()',
        '(pointerenter)': 'onPointer(true, $event)',
        '(focus)': 'onEnter()',
        '(pointerleave)': 'onPointer(false, $event)',
        '(blur)': 'onLeave()',
        '(keydown.tab)': 'onTab($event)',
        '[attr.aria-haspopup]': "'dialog'",
        '[attr.aria-expanded]': "popover().isOpen() ? 'true' : 'false'",
    },
})
export class TumAetUiPopoverTriggerDirective {
    private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
    readonly popover = input.required<TumAetUiPopoverComponent>({ alias: 'tumAetUiPopoverTrigger' });

    protected onClick(): void {
        if (!this.opensOnHover()) {
            this.popover().toggle(this.elementRef);
        }
    }

    protected onPointer(over: boolean, event: PointerEvent): void {
        if (!this.opensOnHover()) {
            return;
        }
        if (event.pointerType === 'touch') {
            // A finger does not hover: it leaves the trigger the moment it is lifted, which must not close the popover.
            if (over) {
                this.popover().openFromTrigger(this.elementRef, true);
            }
            return;
        }
        this.popover().setPointerOverTrigger(over);
        if (over) {
            this.onEnter();
        } else {
            this.onLeave();
        }
    }

    protected onEnter(): void {
        if (this.opensOnHover()) {
            this.popover().openFromTrigger(this.elementRef);
        }
    }

    protected onLeave(): void {
        if (this.opensOnHover()) {
            this.popover().scheduleHoverClose();
        }
    }

    protected onTab(event: Event): void {
        // `keydown.tab` only matches Tab without modifiers, so Shift+Tab leaves the trigger backwards as usual.
        if (this.opensOnHover() && this.popover().isOpen() && this.popover().focusFirstControl()) {
            event.preventDefault();
        }
    }

    private opensOnHover(): boolean {
        return this.popover().openOn() === 'hover';
    }
}
