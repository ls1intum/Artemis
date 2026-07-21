import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Owned content card, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Drop-in replacement for PrimeNG's `p-card`: same surface (content background, 12px radius, the Aura card
 * shadow) and the same body padding/gap, reproduced from the Aura `card` tokens + base style. Content
 * projects into the body via the default slot (the shape the iris KPI card uses); an optional `header` /
 * `subheader` render the Aura caption (title 1.25rem/500, subtitle in the muted color), and dedicated
 * `[tumUiCardHeader]` / `[tumUiCardFooter]` slots cover the remaining p-card regions for a full drop-in.
 * `styleClass` is forwarded onto the card root, exactly like p-card, so callers can keep their own hook
 * class (e.g. `iris-kpi-card`).
 */
@Component({
    selector: 'tum-ui-card',
    templateUrl: './tum-ui-card.component.html',
    styleUrl: './tum-ui-card.component.scss',
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

    // Card surface = Aura content.background (surface.0 light / surface.900 dark, matching the Artemis theme
    // override) with the content text color; radius + shadow live in the stylesheet.
    protected readonly hostClasses = computed(() => `tum-ui-card bg-surface-0 dark:bg-surface-900 text-color ${this.styleClass()}`.trim());
}
