import { Directive, computed, contentChild } from '@angular/core';
import { TumUiListItemActionDirective } from './tum-ui-list-item-action.directive';

// The divider sits on the row rather than the list, so the list's own border is never doubled on the first
// row and a row keeps its separator wherever it is rendered.
const BASE = 'tum-ui-list-item tum:flex tum:min-w-0 tum:flex-col tum:border-t tum:border-border tum:text-text tum:first:border-t-0';

// Only a row without an interactive child pads itself: when the row holds an action, the action takes the
// padding so the entire row, not just its text, is clickable.
const OWN_PADDING = 'tum:px-4 tum:py-3';

/**
 * A single row of a {@link TumUiListComponent}.
 *
 * Applied to a real `<li>` so the list keeps its native semantics. Put a `[tumUiListItemAction]` link or
 * button inside for a row that navigates or acts; leave it out for a plain content row.
 */
@Directive({
    selector: 'li[tumUiListItem]',
    host: {
        '[class]': 'hostClasses()',
    },
})
export class TumUiListItemDirective {
    private readonly action = contentChild(TumUiListItemActionDirective);

    protected readonly hostClasses = computed(() => (this.action() ? BASE : `${BASE} ${OWN_PADDING}`));
}
