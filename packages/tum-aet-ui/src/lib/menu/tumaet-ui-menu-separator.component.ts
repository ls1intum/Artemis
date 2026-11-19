import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * A line between two sets of entries in a {@link TumAetUiMenuComponent}.
 *
 * It is announced as a separator and is not an entry: the arrow keys, Home, End and typeahead skip it.
 *
 * ```html
 * <tumaet-ui-menu>
 *     <button tumAetUiMenuItem>Settings</button>
 *     <tumaet-ui-menu-separator />
 *     <button tumAetUiMenuItem>Sign out</button>
 * </tumaet-ui-menu>
 * ```
 */
@Component({
    selector: 'tumaet-ui-menu-separator',
    template: '',
    host: {
        role: 'separator',
        class: 'tumaet-ui-menu-separator tumaet:my-1 tumaet:block tumaet:shrink-0 tumaet:border-0 tumaet:border-t tumaet:border-solid tumaet:border-border',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiMenuSeparatorComponent {}
