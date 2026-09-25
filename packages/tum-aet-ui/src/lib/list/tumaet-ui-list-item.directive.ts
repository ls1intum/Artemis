import { Directive, booleanAttribute, computed, contentChild, input } from '@angular/core';
import { TumAetUiListItemActionDirective } from './tumaet-ui-list-item-action.directive';

const BASE = 'tumaet-ui-list-item tumaet:flex tumaet:min-w-0 tumaet:border-t tumaet:border-border tumaet:text-text tumaet:first:border-t-0';

const STACKED = 'tumaet:flex-col';

const INLINE = 'tumaet:flex-row tumaet:items-center tumaet:justify-between tumaet:gap-3';

// Interactive children own the padding so the entire row is clickable.
const OWN_PADDING = 'tumaet:px-4 tumaet:py-3';

/**
 * A single row of a {@link TumAetUiListComponent}.
 *
 * Applied to a real `<li>` so the list keeps its native semantics. Put a `[tumAetUiListItemAction]` link or
 * button inside for a row that navigates or acts; leave it out for a plain content row.
 */
@Directive({
    selector: 'li[tumAetUiListItem]',
    host: {
        '[class]': 'hostClasses()',
    },
})
export class TumAetUiListItemDirective {
    /** Places content in a horizontal row instead of a vertical stack. */
    readonly inline = input(false, { transform: booleanAttribute });

    private readonly action = contentChild(TumAetUiListItemActionDirective);

    protected readonly hostClasses = computed(() => {
        const direction = this.inline() ? INLINE : STACKED;
        return this.action() ? `${BASE} ${direction}` : `${BASE} ${direction} ${OWN_PADDING}`;
    });
}
