import { Directive, booleanAttribute, computed, contentChild, input } from '@angular/core';
import { TumUiListItemActionDirective } from './tum-ui-list-item-action.directive';

// The divider sits on the row rather than the list, so the list's own border is never doubled on the first
// row and a row keeps its separator wherever it is rendered.
const BASE = 'tum-ui-list-item tum:flex tum:min-w-0 tum:border-t tum:border-border tum:text-text tum:first:border-t-0';

const STACKED = 'tum:flex-col';

const INLINE = 'tum:flex-row tum:items-center tum:justify-between tum:gap-3';

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
    /**
     * Lays the row out on one line — a label beside its value, or a label beside its control — instead of
     * stacking its content. The row owns its direction because the package stylesheet is unlayered and loads
     * after the host's, so an application `flex-row` utility cannot override it.
     */
    readonly inline = input(false, { transform: booleanAttribute });

    private readonly action = contentChild(TumUiListItemActionDirective);

    protected readonly hostClasses = computed(() => {
        const direction = this.inline() ? INLINE : STACKED;
        return this.action() ? `${BASE} ${direction}` : `${BASE} ${direction} ${OWN_PADDING}`;
    });
}
