import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/**
 * Owned content card, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * A content card with a surface, rounded corners and a soft shadow. Content projects into the body via the
 * default slot; an optional `header` / `subheader` render a caption (title + muted subtitle), and dedicated
 * `[tumUiCardHeader]` / `[tumUiCardFooter]` slots cover the remaining regions. A native `class` on the host
 * merges with the card's own classes, so callers can keep their own hook class (e.g. `iris-kpi-card`).
 *
 * Styled entirely with the design-token utilities (surface / radius / shadow / spacing / typography); no
 * component stylesheet.
 */
@Component({
    selector: 'tum-ui-card',
    templateUrl: './tum-ui-card.component.html',
    // Surface (surface.0 light / surface.900 dark) + content text color, laid out as a column with a rounded,
    // soft-shadowed edge — all via the shared design-token utilities.
    host: {
        class: 'tum-ui-card flex flex-col rounded-xl shadow-sm bg-surface-0 dark:bg-surface-900 text-color',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiCardComponent {
    /** Optional card title, rendered in the Aura caption (parity with p-card `[header]`). */
    readonly header = input<string>();
    /** Optional card subtitle, rendered under the title in the muted color (parity with p-card `[subheader]`). */
    readonly subheader = input<string>();
}
