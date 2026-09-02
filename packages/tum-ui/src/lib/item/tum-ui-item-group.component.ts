import { ChangeDetectionStrategy, Component, Directive, booleanAttribute, computed, inject, input } from '@angular/core';
import { TumUiSize, TumUiSizeAlias, resolveSize } from '../foundation/tum-ui-vocabulary';
import { TumUiItemGroupService } from './tum-ui-item-group.service';

const GROUP_BASE = 'tum-ui-item-group tum:flex tum:min-w-0 tum:flex-col tum:m-0 tum:p-0 tum:list-none';

// `divide-y` draws the rule with a border on the rows themselves, so the list keeps exactly one child per row and
// nothing that is not a row ends up inside `role="list"`.
const GROUP_SEPARATORS = 'tum:divide-y tum:divide-border';

function tumUiItemGroupClasses(separators: boolean): string {
    return separators ? `${GROUP_BASE} ${GROUP_SEPARATORS}` : GROUP_BASE;
}

/**
 * A list of rows.
 *
 * The group exists for three reasons a bare `<div>` cannot cover: it keeps the list semantics a flex container
 * loses, it names the list, and it publishes one row size to every item inside it.
 *
 * The explicit `role="list"` is deliberate, and matches the stepper's: a flex list without markers loses its list
 * semantics in some browsers, and every item depends on the group to carry its `role="listitem"`. The accessible
 * name matters for the same reason a landmark's does — a page with three unnamed lists gives a screen-reader user
 * nothing to choose between.
 *
 * Use the `[tumUiItemGroup]` form on a real `<ul>` when whole rows are links, so the anchors keep their own
 * semantics:
 *
 * ```html
 * <ul tumUiItemGroup ariaLabel="Recent runs">
 *     @for (run of runs(); track run.id) {
 *         <li><a tumUiItem [routerLink]="run.link">…</a></li>
 *     }
 * </ul>
 * ```
 */
@Component({
    selector: 'tum-ui-item-group',
    template: '<ng-content />',
    providers: [TumUiItemGroupService],
    host: {
        role: 'list',
        '[class]': 'hostClasses()',
        '[attr.aria-label]': 'ariaLabel()',
        '[attr.data-slot]': '"item-group"',
        '[attr.data-size]': 'effectiveSize()',
        '[attr.data-separators]': 'separators() || null',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiItemGroupComponent {
    /** Accessible name of the list. Required in practice: an unnamed list is one a user cannot choose between. */
    readonly ariaLabel = input<string>();

    /** Row size applied to every item that does not override it. */
    readonly size = input<TumUiSize | TumUiSizeAlias>('medium');

    /**
     * Draws a rule between rows. It is a group setting rather than an element the consumer inserts, because a rule
     * between every two rows of a list is decoration: as real markup it would put a non-row child inside
     * `role="list"` on every gap.
     */
    readonly separators = input(false, { transform: booleanAttribute });

    protected readonly effectiveSize = computed(() => resolveSize(this.size(), 'tum-ui-item-group'));
    protected readonly hostClasses = computed(() => tumUiItemGroupClasses(this.separators()));

    constructor() {
        inject(TumUiItemGroupService).register(this.effectiveSize);
    }
}

/**
 * The attribute form of {@link TumUiItemGroupComponent}, for a real `<ul>` or `<ol>`.
 *
 * Angular cannot swap a host element, so this is how a consumer keeps native list markup — the only structure in
 * which a whole row can be an `<a>` without the anchor losing its link role.
 */
@Directive({
    selector: '[tumUiItemGroup]',
    providers: [TumUiItemGroupService],
    host: {
        role: 'list',
        '[class]': 'hostClasses()',
        '[attr.aria-label]': 'ariaLabel()',
        '[attr.data-slot]': '"item-group"',
        '[attr.data-size]': 'effectiveSize()',
        '[attr.data-separators]': 'separators() || null',
    },
})
export class TumUiItemGroupDirective {
    /** Accessible name of the list. */
    readonly ariaLabel = input<string>();

    /** Row size applied to every item that does not override it. */
    readonly size = input<TumUiSize | TumUiSizeAlias>('medium');

    /** Draws a rule between rows. */
    readonly separators = input(false, { transform: booleanAttribute });

    protected readonly effectiveSize = computed(() => resolveSize(this.size(), 'tumUiItemGroup'));
    protected readonly hostClasses = computed(() => tumUiItemGroupClasses(this.separators()));

    constructor() {
        inject(TumUiItemGroupService).register(this.effectiveSize);
    }
}
