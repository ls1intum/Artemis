import { Directive, booleanAttribute, computed, input } from '@angular/core';

const BASE =
    'tumaet-ui-list-item-action tumaet:flex tumaet:w-full tumaet:items-center tumaet:gap-2 tumaet:border-0 tumaet:px-4 tumaet:py-3 tumaet:text-start tumaet:text-base tumaet:no-underline ' +
    'tumaet:focus-visible:outline tumaet:focus-visible:outline-2 tumaet:focus-visible:outline-focus tumaet:focus-visible:-outline-offset-2';

// The background lives on the state, not the base: a base `bg-transparent` would win over the active
// background, because both are utilities and neither is more specific.
const INACTIVE = 'tumaet:cursor-pointer tumaet:bg-transparent tumaet:text-text tumaet:hover:bg-hover-background tumaet:hover:text-text-hover';

const ACTIVE = 'tumaet:cursor-pointer tumaet:bg-highlight-background tumaet:font-medium tumaet:text-highlight';

/**
 * Turns the interactive element of a {@link TumAetUiListItemDirective} into the row itself, so the whole row is
 * the click and focus target rather than just the text inside it.
 *
 * Apply it to an `<a>` for navigation — the element stays a real link, so `routerLink` and opening in a new
 * tab keep working — or to a `<button>` for an action:
 *
 * ```html
 * <li tumAetUiListItem>
 *     <a tumAetUiListItemAction routerLink="account" routerLinkActive #link="routerLinkActive" [active]="link.isActive">
 *         Account information
 *     </a>
 * </li>
 * ```
 */
@Directive({
    selector: 'a[tumAetUiListItemAction], button[tumAetUiListItemAction]',
    host: {
        '[class]': 'hostClasses()',
        '[attr.aria-current]': 'active() ? "page" : null',
    },
})
export class TumAetUiListItemActionDirective {
    /** Marks this row as the one currently shown, which sets `aria-current="page"`. */
    readonly active = input(false, { transform: booleanAttribute });

    protected readonly hostClasses = computed(() => `${BASE} ${this.active() ? ACTIVE : INACTIVE}`);
}
