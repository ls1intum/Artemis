import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input } from '@angular/core';
export type HyperionStatusState = 'queued' | 'running' | 'success' | 'warning' | 'danger' | 'neutral' | 'unknown';

/**
 * Compact state indicator: a dot with its state word.
 *
 * The word is the accessible name and is always rendered — hiding it with `showLabel` keeps it available to assistive
 * technology, so colour is never the only signal. Shape carries the states that share the muted colour: `neutral` is a
 * solid dot, `queued` a ring, `unknown` a dashed ring.
 */
@Component({
    selector: 'jhi-hyperion-status',
    templateUrl: './hyperion-status.component.html',
    styleUrl: './hyperion-status.component.scss',
    host: {
        '[attr.data-slot]': '"status-dot"',
        class: 'hyperion-status inline-flex items-center gap-2 text-sm',
        '[attr.role]': "live() ? 'status' : null",
        '[attr.data-state]': 'state()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HyperionStatusComponent {
    /** Semantic state the dot reports. */
    readonly state = input.required<HyperionStatusState>();

    /** Translated human state word; it is the accessible name of the indicator. */
    readonly label = input.required<string>();

    /** Renders the label visually. When disabled the label stays in the accessibility tree. */
    readonly showLabel = input(true, { transform: booleanAttribute });

    /**
     * Announces state changes as a live region. Leave it off unless this dot is the one place a change is reported —
     * a list of dots must not turn into a list of live regions.
     */
    readonly live = input(false, { transform: booleanAttribute });

    protected readonly labelClasses = computed(() => `hyperion-status-label ${this.showLabel() ? '' : 'sr-only'}`.trimEnd());
}
