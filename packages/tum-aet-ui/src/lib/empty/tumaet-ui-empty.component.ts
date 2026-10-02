import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
export type TumAetUiEmptySize = 'small' | 'medium' | 'large';

const EMPTY_BASE = 'tumaet-ui-empty tumaet:flex tumaet:flex-col tumaet:items-center tumaet:justify-center tumaet:text-center tumaet:text-text';

const EMPTY_SIZE: Record<TumAetUiEmptySize, string> = {
    small: 'tumaet:gap-2 tumaet:px-3 tumaet:py-4',
    medium: 'tumaet:gap-3 tumaet:px-4 tumaet:py-8',
    large: 'tumaet:gap-4 tumaet:px-6 tumaet:py-14',
};

/**
 * The place where something would be, when there is nothing there yet.
 *
 * "Empty" is a state a surface is in, not a message it prints, so this component owns the shape and the consumer
 * owns every word: there is no `title` or `description` string input, only slots. Compose it from
 * `tumaet-ui-empty-header` (with `-media`, `-title` and `-description` inside) and `tumaet-ui-empty-content` for the
 * action that resolves the emptiness.
 *
 * ```html
 * <tumaet-ui-empty size="small">
 *     <tumaet-ui-empty-header>
 *         <tumaet-ui-empty-media variant="icon"><fa-icon [icon]="faInbox" /></tumaet-ui-empty-media>
 *         <tumaet-ui-empty-title>Nothing here yet</tumaet-ui-empty-title>
 *         <tumaet-ui-empty-description>Items you add will appear in this list.</tumaet-ui-empty-description>
 *     </tumaet-ui-empty-header>
 *     <tumaet-ui-empty-content><tumaet-ui-button size="small">Add an item</tumaet-ui-button></tumaet-ui-empty-content>
 * </tumaet-ui-empty>
 * ```
 *
 * **It carries no role, deliberately.** An empty state is ambient: it is what the region looks like, not an event
 * that just happened, so it must not be a live region and must not announce itself. It is also not a heading —
 * `tumaet-ui-empty-title` renders a paragraph, and a consumer replacing a titled section keeps their own heading
 * above it.
 *
 * Give it an action, or name who has one. An empty state with neither is an apology.
 */
@Component({
    selector: 'tumaet-ui-empty',
    template: '<ng-content />',
    host: {
        '[class]': 'hostClasses()',
        '[attr.data-slot]': '"empty"',
        '[attr.data-size]': 'effectiveSize()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiEmptyComponent {
    /** Vertical room the placeholder claims. Use `small` inside a card or a panel, `large` for a whole page. */
    readonly size = input<TumAetUiEmptySize>('medium');

    protected readonly effectiveSize = computed(() => this.size());
    protected readonly hostClasses = computed(() => `${EMPTY_BASE} ${EMPTY_SIZE[this.effectiveSize()]}`);
}
