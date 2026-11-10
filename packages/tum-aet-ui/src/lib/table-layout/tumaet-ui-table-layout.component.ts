import { ChangeDetectionStrategy, Component, booleanAttribute, input } from '@angular/core';
import { TumAetUiProgressSpinnerComponent } from '../progress-spinner/tumaet-ui-progress-spinner.component';

/**
 * Frame shared by every data table: a sticky toolbar of at most 40px, a body that is the only part that scrolls
 * (with the 30px column header of `table[tumAetUiTable]` stuck to its top) and a sticky footer of at most 40px.
 *
 * ```html
 * <tumaet-ui-table-layout [loading]="loading()">
 *     <div tumAetUiTableToolbar>search, filters, actions</div>
 *     <table tumAetUiTable>…</table>
 *     <tumaet-ui-paginator tumAetUiTableFooter … />
 * </tumaet-ui-table-layout>
 * ```
 *
 * Do not wrap a slotted node in `@if`: with preserved whitespace Angular no longer projects it (NG8011). Set `[hidden]` on
 * the node instead, or put the `@if` inside it.
 *
 * The frame is as tall as its table up to the free height of the viewport, so a short table has no empty space under
 * it and a long one keeps toolbar and footer in view. Override the offset with `--table-layout-offset`
 * (the height of everything around the table, default 12rem) or give the frame a height with `fill` when a parent
 * already provides one.
 */
@Component({
    selector: 'tumaet-ui-table-layout',
    templateUrl: './tumaet-ui-table-layout.component.html',
    styleUrl: './tumaet-ui-table-layout.component.scss',
    imports: [TumAetUiProgressSpinnerComponent],
    host: { '[class.tumaet-ui-table-layout-fill]': 'fill()', '[attr.aria-busy]': "loading() ? 'true' : null" },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiTableLayoutComponent {
    /** Dims the body and shows a spinner while the consumer loads rows. */
    readonly loading = input(false, { transform: booleanAttribute });
    /** Takes the height of a flex-column parent instead of sizing to the table. */
    readonly fill = input(false, { transform: booleanAttribute });
}
