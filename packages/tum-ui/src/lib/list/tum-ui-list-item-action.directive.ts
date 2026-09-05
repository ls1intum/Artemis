import { Directive, booleanAttribute, computed, input } from '@angular/core';

const BASE =
    'tum-ui-list-item-action tum:flex tum:w-full tum:items-center tum:gap-2 tum:border-0 tum:px-4 tum:py-3 tum:text-start tum:text-base tum:no-underline ' +
    'tum:focus-visible:outline tum:focus-visible:outline-2 tum:focus-visible:outline-focus tum:focus-visible:-outline-offset-2';

// The background lives on the state, not the base: a base `bg-transparent` would win over the active
// background, because both are utilities and neither is more specific.
const INACTIVE = 'tum:cursor-pointer tum:bg-transparent tum:text-text tum:hover:bg-hover-background tum:hover:text-text-hover';

const ACTIVE = 'tum:cursor-pointer tum:bg-highlight-background tum:font-medium tum:text-highlight';

/**
 * Turns the interactive element of a {@link TumUiListItemDirective} into the row itself, so the whole row is
 * the click and focus target rather than just the text inside it.
 *
 * Apply it to an `<a>` for navigation — the element stays a real link, so `routerLink` and opening in a new
 * tab keep working — or to a `<button>` for an action:
 *
 * ```html
 * <li tumUiListItem>
 *     <a tumUiListItemAction routerLink="account" routerLinkActive #link="routerLinkActive" [active]="link.isActive">
 *         Account information
 *     </a>
 * </li>
 * ```
 */
@Directive({
    selector: 'a[tumUiListItemAction], button[tumUiListItemAction]',
    host: {
        '[class]': 'hostClasses()',
        '[attr.aria-current]': 'active() ? "page" : null',
    },
})
export class TumUiListItemActionDirective {
    /** Marks this row as the one currently shown, which sets `aria-current="page"`. */
    readonly active = input(false, { transform: booleanAttribute });

    protected readonly hostClasses = computed(() => `${BASE} ${this.active() ? ACTIVE : INACTIVE}`);
}
