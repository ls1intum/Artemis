import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumUiSize, TumUiSizeAlias, resolveSize } from '../foundation/tum-ui-vocabulary';

const EMPTY_BASE = 'tum-ui-empty tum:flex tum:flex-col tum:items-center tum:justify-center tum:text-center tum:text-text';

const EMPTY_SIZE: Record<TumUiSize, string> = {
    small: 'tum:gap-2 tum:px-3 tum:py-4',
    medium: 'tum:gap-3 tum:px-4 tum:py-8',
    large: 'tum:gap-4 tum:px-6 tum:py-14',
};

/**
 * The place where something would be, when there is nothing there yet.
 *
 * "Empty" is a state a surface is in, not a message it prints, so this component owns the shape and the consumer
 * owns every word: there is no `title` or `description` string input, only slots. Compose it from
 * `tum-ui-empty-header` (with `-media`, `-title` and `-description` inside) and `tum-ui-empty-content` for the
 * action that resolves the emptiness.
 *
 * ```html
 * <tum-ui-empty size="small">
 *     <tum-ui-empty-header>
 *         <tum-ui-empty-media variant="icon"><fa-icon [icon]="faInbox" /></tum-ui-empty-media>
 *         <tum-ui-empty-title>Nothing here yet</tum-ui-empty-title>
 *         <tum-ui-empty-description>Items you add will appear in this list.</tum-ui-empty-description>
 *     </tum-ui-empty-header>
 *     <tum-ui-empty-content><tum-ui-button size="small">Add an item</tum-ui-button></tum-ui-empty-content>
 * </tum-ui-empty>
 * ```
 *
 * **It carries no role, deliberately.** An empty state is ambient: it is what the region looks like, not an event
 * that just happened, so it must not be a live region and must not announce itself. It is also not a heading —
 * `tum-ui-empty-title` renders a paragraph, and a consumer replacing a titled section keeps their own heading
 * above it.
 *
 * Give it an action, or name who has one. An empty state with neither is an apology.
 */
@Component({
    selector: 'tum-ui-empty',
    template: '<ng-content />',
    host: {
        '[class]': 'hostClasses()',
        '[attr.data-slot]': '"empty"',
        '[attr.data-size]': 'effectiveSize()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiEmptyComponent {
    /** Vertical room the placeholder claims. Use `small` inside a card or a panel, `large` for a whole page. */
    readonly size = input<TumUiSize | TumUiSizeAlias>('medium');

    protected readonly effectiveSize = computed(() => resolveSize(this.size(), 'tum-ui-empty'));
    protected readonly hostClasses = computed(() => `${EMPTY_BASE} ${EMPTY_SIZE[this.effectiveSize()]}`);
}
