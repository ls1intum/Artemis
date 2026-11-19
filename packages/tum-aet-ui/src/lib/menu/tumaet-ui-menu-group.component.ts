import { ChangeDetectionStrategy, Component, input } from '@angular/core';

let nextMenuGroupId = 0;

/**
 * A set of related entries in a {@link TumAetUiMenuComponent} under a visible label.
 *
 * The group is announced with its label when focus enters it. The label is text, not an entry: the arrow keys, Home,
 * End and typeahead move between the entries only, across groups.
 *
 * ```html
 * <tumaet-ui-menu>
 *     <button tumAetUiMenuItem>Settings</button>
 *     <tumaet-ui-menu-separator />
 *     <tumaet-ui-menu-group label="Language">
 *         <button tumAetUiMenuItem>English</button>
 *         <button tumAetUiMenuItem>Deutsch</button>
 *     </tumaet-ui-menu-group>
 * </tumaet-ui-menu>
 * ```
 *
 * The label follows `--tumaet-ui-menu-group-label-padding` and `--tumaet-ui-menu-group-label-font-size`, set on the group
 * or an ancestor, so a menu with its own entry size can align the label with its entries.
 *
 * Nested groups are not supported.
 */
@Component({
    selector: 'tumaet-ui-menu-group',
    template: `
        <div class="tumaet-ui-menu-group-label tumaet:leading-tight tumaet:font-medium tumaet:whitespace-nowrap tumaet:text-muted" [id]="labelId">
            {{ label() }}
        </div>
        <ng-content />
    `,
    styleUrl: './tumaet-ui-menu-group.component.scss',
    host: {
        role: 'group',
        class: 'tumaet-ui-menu-group tumaet:flex tumaet:flex-col',
        '[attr.aria-labelledby]': 'labelId',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiMenuGroupComponent {
    /** Text shown above the entries; it also names the group for assistive technology. */
    readonly label = input.required<string>();

    protected readonly labelId = `tumaet-ui-menu-group-label-${nextMenuGroupId++}`;
}
