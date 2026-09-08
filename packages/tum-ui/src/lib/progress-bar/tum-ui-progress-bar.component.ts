import { ChangeDetectionStrategy, Component, afterNextRender, booleanAttribute, computed, input, numberAttribute, signal } from '@angular/core';
import { TumUiSeverity, TumUiSeverityAlias, resolveSeverity } from '../foundation/tum-ui-vocabulary';

/** Severities a progress bar can carry. A subset of {@link TumUiSeverity}. */
export type TumUiProgressBarSeverity = Extract<TumUiSeverity, 'primary' | 'success' | 'warning' | 'danger' | 'info'>;
export type TumUiProgressBarSize = 'small' | 'default';

/**
 * Determinate progress on a known `min`…`max` scale. Use a spinner or status text when the total is unknown.
 * Supply `valueText` to describe units or a denominator for both the visible label and assistive technology.
 * The initial value renders without animation; later changes transition in 400 ms.
 */
@Component({
    selector: 'tum-ui-progress-bar',
    templateUrl: './tum-ui-progress-bar.component.html',
    styleUrl: './tum-ui-progress-bar.component.scss',
    host: {
        class: 'tum-ui-progress-bar',
        role: 'progressbar',
        '[attr.data-size]': 'size()',
        '[attr.aria-valuemin]': 'min()',
        '[attr.aria-valuemax]': 'max()',
        '[attr.aria-valuenow]': 'clampedValue()',
        '[attr.aria-valuetext]': 'valueText() ?? null',
        '[attr.aria-label]': 'ariaLabel()',
        '[attr.data-slot]': '"progress-bar"',
        '[attr.data-severity]': 'effectiveSeverity()',
        '[attr.data-committed]': 'committed() || null',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiProgressBarComponent {
    /** Current value, on the `min`…`max` scale. */
    readonly value = input(0, { transform: numberAttribute });

    /** Floor of the scale. */
    readonly min = input(0, { transform: numberAttribute });

    /** Ceiling of the scale. Only set a ceiling you actually have. */
    readonly max = input(100, { transform: numberAttribute });

    /** Accessible name. A `role="progressbar"` without one is an unnamed reading. */
    readonly ariaLabel = input<string>();

    /**
     * The value in words, for both the visible label and `aria-valuetext` — "17 of 42 files", "€1.24 of €5.00".
     * The consumer owns it, because precision, unit and locale are the consumer's decisions, not the meter's.
     */
    readonly valueText = input<string>();

    /** Renders `valueText` beside the bar. Projected content replaces it. */
    readonly showValue = input(true, { transform: booleanAttribute });

    readonly size = input<TumUiProgressBarSize>('default');

    /** Colour role of the filled track. `warn` is accepted as a deprecated spelling of `warning`. */
    readonly severity = input<TumUiProgressBarSeverity | TumUiSeverityAlias>('primary');

    protected readonly effectiveSeverity = computed(() => resolveSeverity<TumUiProgressBarSeverity>(this.severity(), 'tum-ui-progress-bar'));

    protected readonly clampedValue = computed(() => {
        const value = this.value();
        if (!Number.isFinite(value)) {
            return this.min();
        }
        return Math.max(this.min(), Math.min(this.max(), value));
    });

    /** Position of the fill, as a percentage of the scale. A zero-width scale reports zero rather than dividing by it. */
    protected readonly fillPercentage = computed(() => {
        const span = this.max() - this.min();
        return span > 0 ? ((this.clampedValue() - this.min()) / span) * 100 : 0;
    });

    protected readonly label = computed(() => (this.showValue() ? this.valueText() : undefined));

    // Enable transitions only after rendering the initial value.
    private readonly firstPaintDone = signal(false);
    protected readonly committed = this.firstPaintDone.asReadonly();

    constructor() {
        afterNextRender(() => this.firstPaintDone.set(true));
    }
}
