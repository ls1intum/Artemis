import { Directive, ElementRef, inject, input } from '@angular/core';
import { TumAetUiPopoverComponent } from './tumaet-ui-popover.component';

/** How long after a finger was lifted its click may arrive and still count as the same tap. */
const TAP_CLICK_WINDOW_MS = 1000;

/**
 * Wires a trigger element to a {@link TumAetUiPopoverComponent}, anchors the popover to the trigger, and reflects
 * `aria-haspopup`/`aria-expanded` on it for accessibility.
 *
 * What opens the popover follows its `openOn` input. By default a click toggles it. With `openOn="hover"` it opens
 * while the pointer or the keyboard focus is on the trigger, a click is left to the trigger's own handler, and Tab
 * moves from the trigger into the open panel. Opened by a tap, it stays until it is dismissed.
 *
 * Usage: `<button [tumAetUiPopoverTrigger]="pop">Details</button> <tumaet-ui-popover #pop>...</tumaet-ui-popover>`
 */
@Directive({
    selector: '[tumAetUiPopoverTrigger]',
    host: {
        '(click)': 'onClick($event)',
        '(pointerenter)': 'onPointer(true, $event)',
        '(focus)': 'onEnter()',
        '(pointerleave)': 'onPointer(false, $event)',
        '(pointerup)': 'onPointerUp($event)',
        '(blur)': 'onLeave()',
        '(keydown.tab)': 'onTab($event)',
        '[attr.aria-haspopup]': "'dialog'",
        '[attr.aria-expanded]': "popover().isOpen() ? 'true' : 'false'",
    },
})
export class TumAetUiPopoverTriggerDirective {
    private readonly elementRef = inject<ElementRef<HTMLElement>>(ElementRef);
    /** When a finger was last lifted on the trigger, in the time base of the events. */
    private touchReleasedAt?: number;
    readonly popover = input.required<TumAetUiPopoverComponent>({ alias: 'tumAetUiPopoverTrigger' });

    protected onClick(event: Event): void {
        if (!this.opensOnHover()) {
            this.popover().toggle(this.elementRef);
            return;
        }
        // Only the click tells that a touch was a tap: a scroll ends in `pointercancel`, and a finger that is dragged
        // off the trigger may still be lifted "on" it, but neither is followed by a click.
        const releasedAt = this.touchReleasedAt;
        this.touchReleasedAt = undefined;
        if (releasedAt !== undefined && event.timeStamp - releasedAt < TAP_CLICK_WINDOW_MS) {
            this.popover().openFromTrigger(this.elementRef, true);
        }
    }

    protected onPointer(over: boolean, event: PointerEvent): void {
        if (!this.opensOnHover()) {
            return;
        }
        if (event.pointerType === 'touch') {
            // A finger does not hover. It enters before the browser knows whether this is a tap or the start of a
            // scroll, and it leaves the moment it is lifted, so neither opens or closes; see onClick.
            return;
        }
        this.popover().setPointerOverTrigger(over);
        if (over) {
            this.onEnter();
        } else {
            this.onLeave();
        }
    }

    protected onPointerUp(event: PointerEvent): void {
        this.touchReleasedAt = this.opensOnHover() && event.pointerType === 'touch' ? event.timeStamp : undefined;
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
