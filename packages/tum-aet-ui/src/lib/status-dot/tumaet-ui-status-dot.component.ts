import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input } from '@angular/core';
export type TumAetUiStatusDotState = 'queued' | 'running' | 'success' | 'warning' | 'danger' | 'neutral' | 'unknown';

/**
 * Compact state indicator: a dot with its state word.
 *
 * The word is the accessible name and is always rendered — hiding it with `showLabel` keeps it available to assistive
 * technology, so colour is never the only signal. Shape carries the states that share the muted colour: `neutral` is a
 * solid dot, `queued` a ring, `unknown` a dashed ring.
 */
@Component({
    selector: 'tumaet-ui-status-dot',
    templateUrl: './tumaet-ui-status-dot.component.html',
    styleUrl: './tumaet-ui-status-dot.component.scss',
    host: {
        '[attr.data-slot]': '"status-dot"',
        class: 'tumaet-ui-status-dot tumaet:inline-flex tumaet:items-center tumaet:gap-2 tumaet:text-sm tumaet:text-text',
        '[attr.role]': "live() ? 'status' : null",
        '[attr.data-state]': 'state()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumAetUiStatusDotComponent {
    /** Semantic state the dot reports. */
    readonly state = input.required<TumAetUiStatusDotState>();

    /** Translated human state word; it is the accessible name of the indicator. */
    readonly label = input.required<string>();

    /** Renders the label visually. When disabled the label stays in the accessibility tree. */
    readonly showLabel = input(true, { transform: booleanAttribute });

    /**
     * Announces state changes as a live region. Leave it off unless this dot is the one place a change is reported —
     * a list of dots must not turn into a list of live regions.
     */
    readonly live = input(false, { transform: booleanAttribute });

    protected readonly labelClasses = computed(() => `tumaet-ui-status-dot-label ${this.showLabel() ? '' : 'tumaet:sr-only'}`.trimEnd());
}
