import { Directive, booleanAttribute, computed, contentChild, input } from '@angular/core';
import { TumUiListItemActionDirective } from './tum-ui-list-item-action.directive';

const BASE = 'tum-ui-list-item tum:flex tum:min-w-0 tum:border-t tum:border-border tum:text-text tum:first:border-t-0';

const STACKED = 'tum:flex-col';

const INLINE = 'tum:flex-row tum:items-center tum:justify-between tum:gap-3';

// Interactive children own the padding so the entire row is clickable.
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
    /** Places content in a horizontal row instead of a vertical stack. */
    readonly inline = input(false, { transform: booleanAttribute });

    private readonly action = contentChild(TumUiListItemActionDirective);

    protected readonly hostClasses = computed(() => {
        const direction = this.inline() ? INLINE : STACKED;
        return this.action() ? `${BASE} ${direction}` : `${BASE} ${direction} ${OWN_PADDING}`;
    });
}
