import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumUiSize, TumUiSizeAlias, resolveSize } from '../foundation/tum-ui-vocabulary';

const SPINNER_SIZE: Record<TumUiSize, string> = {
    small: 'tum-ui-progress-spinner-small',
    medium: 'tum-ui-progress-spinner-medium',
    large: 'tum-ui-progress-spinner-large',
};

/**
 * Indeterminate activity: something is happening and its end is not calculable.
 *
 * Use it for a wait of roughly one to ten seconds. Past that a spinner stops informing — it looks the same at
 * minute one and minute fourteen — and the surface owes the reader a stage, an elapsed time and a way out.
 *
 * `ariaLabel` is required in practice: the host is a `role="status"`, and an unnamed live region is the one thing
 * a live region must not be. Say what is loading, not "loading".
 *
 * Under `prefers-reduced-motion` the arc is replaced by a static ring rather than frozen in place. A stopped
 * spinner is not a reduced-motion fallback; it is a broken graphic, and it reads as a hung page.
 */
@Component({
    selector: 'tum-ui-progress-spinner',
    templateUrl: './tum-ui-progress-spinner.component.html',
    styleUrl: './tum-ui-progress-spinner.component.scss',
    host: {
        '[class]': 'hostClasses()',
        role: 'status',
        'aria-busy': 'true',
        '[attr.aria-label]': 'ariaLabel()',
        '[attr.data-slot]': '"progress-spinner"',
        '[attr.data-size]': 'effectiveSize()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiProgressSpinnerComponent {
    /** Accessible name. Name the thing being waited for, not the act of waiting. */
    readonly ariaLabel = input<string>();

    /**
     * Diameter step: `small` sits inline with text, `medium` beside a paragraph, `large` fills a loading region.
     * Override it entirely with `--tum-ui-progress-spinner-size` where a specific box has to be matched.
     */
    readonly size = input<TumUiSize | TumUiSizeAlias>('large');

    protected readonly effectiveSize = computed(() => resolveSize(this.size(), 'tum-ui-progress-spinner', 'large'));
    protected readonly hostClasses = computed(() => `tum-ui-progress-spinner ${SPINNER_SIZE[this.effectiveSize()]}`);
}
