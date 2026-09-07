import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Bordered, vertically stacked list of {@link TumUiListItemDirective} entries, for a settings section, a
 * navigation column, or a short record of label / value rows.
 *
 * The list owns the outer border and radius and the rows own the divider between them, so a host never has
 * to reach for a border colour of its own:
 *
 * ```html
 * <tum-ui-list ariaLabel="User settings">
 *     <li tumUiListItem>
 *         <a tumUiListItemAction routerLink="account" routerLinkActive #link="routerLinkActive" [active]="link.isActive">
 *             Account information
 *         </a>
 *     </li>
 *     <li tumUiListItem>Joined Artemis in 2021</li>
 * </tum-ui-list>
 * ```
 *
 * Use `tum-ui-table` instead when the content is tabular and needs sorting, selection, or column headers.
 */
@Component({
    selector: 'tum-ui-list',
    templateUrl: './tum-ui-list.component.html',
    styleUrl: './tum-ui-list.component.scss',
    host: {
        class: 'tum-ui-list',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiListComponent {
    /** Accessible name for the list. Set it when the list has no visible heading beside it. */
    readonly ariaLabel = input<string>();

    /** Id of the visible heading that names the list. Prefer this over `ariaLabel` when a heading exists. */
    readonly ariaLabelledBy = input<string>();
}
