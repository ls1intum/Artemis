import { ChangeDetectionStrategy, Component, Directive, ElementRef, computed, inject, input } from '@angular/core';
import { TumUiSize, TumUiSizeAlias, resolveSize } from '../foundation/tum-ui-vocabulary';
import { TumUiItemGroupService } from './tum-ui-item-group.service';
import { TumUiItemVariant, tumUiItemClasses } from './tum-ui-item.variants';

/** Elements whose own role a layout directive must never overwrite. */
const INTERACTIVE_TAGS = new Set(['A', 'BUTTON', 'SUMMARY']);

/**
 * One row of a {@link TumUiItemGroupComponent}: a leading graphic, a stack of text, and trailing actions.
 *
 * This is the media / content / actions row every application ends up rewriting, so it is here once. It owns
 * layout, size and surface treatment only — every word, glyph and control is projected, and any state beyond
 * `variant` belongs on the consumer's own `data-*` attribute, which the row's class list is built not to fight.
 *
 * ```html
 * <tum-ui-item>
 *     <tum-ui-item-media><fa-icon [icon]="faFile" /></tum-ui-item-media>
 *     <tum-ui-item-content>
 *         <tum-ui-item-title>src/Sorter.java</tum-ui-item-title>
 *         <tum-ui-item-description>Modified two minutes ago</tum-ui-item-description>
 *     </tum-ui-item-content>
 *     <tum-ui-item-actions><tum-ui-tag size="small">Modified</tum-ui-tag></tum-ui-item-actions>
 * </tum-ui-item>
 * ```
 *
 * The row is not focusable and not clickable. A row the user can activate is a link or a button, and gets there
 * through the `[tumUiItem]` directive on a real `<a>` or `<button>` — never through a click handler on a `<div>`.
 *
 * It publishes `role="listitem"` only inside a {@link TumUiItemGroupComponent}. Used on its own — a single summary
 * row, a header line — it stays a plain element, because that role with no list around it is an ARIA violation.
 */
@Component({
    selector: 'tum-ui-item',
    template: '<ng-content />',
    host: {
        // Published only inside a group. `role="listitem"` with no `list` ancestor is an ARIA violation, so a row
        // used on its own — a single summary row, a card header — stays a plain element.
        '[attr.role]': 'hostRole',
        '[class]': 'hostClasses()',
        '[attr.data-slot]': '"item"',
        '[attr.data-variant]': 'variant()',
        '[attr.data-size]': 'effectiveSize()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiItemComponent {
    private readonly group = inject(TumUiItemGroupService, { optional: true });

    /** `listitem` inside a group, nothing outside one. Fixed for the row's lifetime, like the element it sits in. */
    protected readonly hostRole = this.group ? 'listitem' : null;

    /** Surface treatment. `default` inside a group, `outline` for a row that stands alone. */
    readonly variant = input<TumUiItemVariant>('default');

    /** Row size. Omit it to follow the enclosing group; a row with no group falls back to `medium`. */
    readonly size = input<TumUiSize | TumUiSizeAlias>();

    protected readonly effectiveSize = computed(() => {
        const own = this.size();
        return own === undefined ? (this.group?.size() ?? 'medium') : resolveSize(own, 'tum-ui-item');
    });

    protected readonly hostClasses = computed(() => tumUiItemClasses({ variant: this.variant(), size: this.effectiveSize(), interactive: false }));
}

/**
 * The attribute form of {@link TumUiItemComponent}, for when the row itself has to be a different element.
 *
 * Angular cannot swap a host element, so this directive is the package's `asChild`: put it on an `<a>` for a row
 * that navigates, a `<button>` for a row that acts, or an `<li>` inside a native list. It shares one variants
 * table with the component, so the two cannot drift apart.
 *
 * **It never overwrites an interactive element's role.** On an `<a>` or a `<button>` the host keeps its own
 * semantics and gains hover and focus affordances. On anything else, and only inside a group, it publishes
 * `role="listitem"` — which is what a `<div>` inside a `role="list"` needs, and what restores an `<li>` that flex
 * layout has stripped. Outside a group it adds no role at all, because `listitem` with no `list` ancestor is an
 * ARIA violation of its own.
 */
@Directive({
    selector: '[tumUiItem]',
    host: {
        '[class]': 'hostClasses()',
        '[attr.role]': 'hostRole',
        '[attr.data-slot]': '"item"',
        '[attr.data-variant]': 'variant()',
        '[attr.data-size]': 'effectiveSize()',
    },
})
export class TumUiItemDirective {
    private readonly group = inject(TumUiItemGroupService, { optional: true });
    private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

    /** Surface treatment. */
    readonly variant = input<TumUiItemVariant>('default');

    /** Row size. Omit it to follow the enclosing group. */
    readonly size = input<TumUiSize | TumUiSizeAlias>();

    /** True when the host brings its own interactive role, and therefore its own hover and focus behaviour. */
    protected readonly interactive = INTERACTIVE_TAGS.has(this.element.tagName);

    // Read once, because the host element never changes. A role the consumer wrote in the template is echoed back
    // rather than overwritten — `[attr.role]` bound to null *removes* the attribute, so the existing value has to
    // be carried forward explicitly. `null` on an untouched link leaves it a link, and `listitem` is only added
    // inside a group, because that role with no `list` ancestor is an ARIA violation in its own right.
    protected readonly hostRole = this.element.getAttribute('role') ?? (this.interactive || !this.group ? null : 'listitem');

    protected readonly effectiveSize = computed(() => {
        const own = this.size();
        return own === undefined ? (this.group?.size() ?? 'medium') : resolveSize(own, 'tumUiItem');
    });

    protected readonly hostClasses = computed(() => tumUiItemClasses({ variant: this.variant(), size: this.effectiveSize(), interactive: this.interactive }));
}
