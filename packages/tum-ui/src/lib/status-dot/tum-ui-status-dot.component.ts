import { ChangeDetectionStrategy, Component, booleanAttribute, computed, input } from '@angular/core';

export type TumUiStatusDotState = 'queued' | 'running' | 'success' | 'warning' | 'error' | 'neutral';

const STATUS_DOT_BASE = 'tum-ui-status-dot-indicator tum:size-2 tum:shrink-0 tum:rounded-full';

const STATUS_DOT_STATE: Record<TumUiStatusDotState, string> = {
    queued: 'tum:bg-muted',
    running: 'tum:bg-state-info',
    success: 'tum:bg-state-success',
    warning: 'tum:bg-state-warning',
    error: 'tum:bg-state-danger',
    neutral: 'tum:bg-muted',
};

/** Only states that are still moving pulse; a terminal state never animates. */
const STATUS_DOT_PULSING: readonly TumUiStatusDotState[] = ['queued', 'running'];

/**
 * Compact state indicator: a coloured dot with its state word.
 *
 * The word is the accessible name and is always rendered — hiding it with `showLabel` keeps it available to assistive
 * technology, so colour is never the only signal.
 */
@Component({
    selector: 'tum-ui-status-dot',
    templateUrl: './tum-ui-status-dot.component.html',
    styleUrl: './tum-ui-status-dot.component.scss',
    host: {
        class: 'tum-ui-status-dot tum:inline-flex tum:items-center tum:gap-2 tum:text-sm tum:text-text',
        '[attr.role]': "live() ? 'status' : null",
        '[attr.data-state]': 'state()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiStatusDotComponent {
    /** Semantic state the dot reports. */
    readonly state = input.required<TumUiStatusDotState>();

    /** Translated human state word; it is the accessible name of the indicator. */
    readonly label = input.required<string>();

    /** Renders the label visually. When disabled the label stays in the accessibility tree. */
    readonly showLabel = input(true, { transform: booleanAttribute });

    /**
     * Announces state changes as a live region. Leave it off unless this dot is the one place a change is reported —
     * a list of dots must not turn into a list of live regions.
     */
    readonly live = input(false, { transform: booleanAttribute });

    protected readonly dotClasses = computed(() => {
        const pulse = STATUS_DOT_PULSING.includes(this.state()) ? 'tum-ui-status-dot-pulse' : '';
        return `${STATUS_DOT_BASE} ${STATUS_DOT_STATE[this.state()]} ${pulse}`.trimEnd();
    });

    protected readonly labelClasses = computed(() => `tum-ui-status-dot-label ${this.showLabel() ? '' : 'tum:sr-only'}`.trimEnd());
}
