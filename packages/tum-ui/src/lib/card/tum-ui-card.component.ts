import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Content card.
 *
 * A content card with a surface, rounded corners and a soft shadow. Content projects into the body via the
 * default slot; an optional `header` / `subheader` render a caption (title + muted subtitle), and dedicated
 * `[tumUiCardHeader]` / `[tumUiCardFooter]` slots cover the remaining regions. `styleClass` is forwarded onto
 * the card root so callers can keep their own hook class (e.g. `iris-kpi-card`).
 *
 * Styled entirely with the design-token utilities (surface / radius / shadow / spacing / typography); no
 * component stylesheet.
 */
@Component({
    selector: 'tum-ui-card',
    templateUrl: './tum-ui-card.component.html',
    host: {
        '[class]': 'hostClasses()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiCardComponent {
    /** Optional card title, rendered in the Aura caption (parity with p-card `[header]`). */
    readonly header = input<string>();
    /** Optional card subtitle, rendered under the title in the muted color (parity with p-card `[subheader]`). */
    readonly subheader = input<string>();
    /** Extra classes forwarded onto the card root (drop-in for p-card `styleClass`). */
    readonly styleClass = input<string>('');

    protected readonly hostClasses = computed(() =>
        `tum-ui-card flex flex-col rounded-xl shadow-sm bg-tum-ui-surface-0 dark:bg-tum-ui-surface-900 text-tum-ui-text ${this.styleClass()}`.trim(),
    );
}
