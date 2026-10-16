import { Directive, ElementRef, inject, output } from '@angular/core';
import { MenuItem } from '@angular/aria/menu';

/**
 * A single command or navigation entry inside a {@link TumAetUiMenuComponent}.
 *
 * Apply it to a `<button>` for an action, or to an `<a>` for navigation so the entry keeps native link
 * behaviour such as opening in a new tab. The Angular Aria menu item owns the `role`, the roving `tabindex`, and
 * `aria-disabled`; the menu closes once an entry runs and then emits `triggered`.
 *
 * `disabled` takes a boolean binding, `[disabled]="true"`. A disabled entry stays focusable, so it can be discovered
 * with the arrow keys and is announced as unavailable, but it cannot be chosen.
 */
@Directive({
    selector: '[tumAetUiMenuItem]',
    hostDirectives: [{ directive: MenuItem, inputs: ['disabled'] }],
    host: {
        // Lets the Angular Aria test harnesses (`@angular/aria/menu/testing`) find the entry.
        ngMenuItem: '',
        class:
            'tumaet-ui-menu-item tumaet:flex tumaet:cursor-pointer tumaet:items-center tumaet:gap-2 tumaet:border-0 tumaet:bg-transparent tumaet:px-3 tumaet:py-2 tumaet:text-start tumaet:text-base tumaet:text-text tumaet:no-underline ' +
            'tumaet:hover:bg-hover-background tumaet:hover:text-text-hover tumaet:focus-visible:bg-highlight-focus-background tumaet:focus-visible:text-highlight tumaet:focus-visible:outline-none ' +
            'tumaet:aria-disabled:pointer-events-none tumaet:aria-disabled:cursor-default tumaet:aria-disabled:text-disabled',
        '(keydown.enter)': 'onEnter($event)',
    },
})
export class TumAetUiMenuItemDirective {
    private readonly menuItem = inject<MenuItem<unknown>>(MenuItem);
    private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

    /** Emits when the entry is chosen with a click, Enter, or Space; never while it is disabled. */
    readonly triggered = output<void>();

    /** @internal Whether this is the entry aria moves focus to and chooses from the keyboard. */
    isActive(): boolean {
        return this.menuItem.active();
    }

    /** @internal Whether the event target lies inside this entry. */
    contains(target: EventTarget | null): boolean {
        return target instanceof Node && this.element.contains(target);
    }

    /** @internal Copies the rendered label into the search term aria's typeahead matches against. */
    syncSearchTerm(): void {
        this.menuItem.searchTerm.set(this.element.textContent?.trim() ?? '');
    }

    /** @internal Emits `triggered` once the menu has chosen this entry. */
    emitTriggered(): void {
        this.triggered.emit();
    }

    protected onEnter(event: Event): void {
        // Aria handles Enter on the menu and cancels it, which would keep a link from navigating. A link follows its
        // own activation instead: the browser turns Enter into a click, which the menu treats like a pointer click.
        // `keydown.enter` only matches Enter without modifiers, so Shift or Ctrl with Enter keeps the browser's meaning.
        const isLink = this.element instanceof HTMLAnchorElement && this.element.hasAttribute('href');
        if (isLink && !this.menuItem.disabled()) {
            event.stopPropagation();
        }
    }
}
