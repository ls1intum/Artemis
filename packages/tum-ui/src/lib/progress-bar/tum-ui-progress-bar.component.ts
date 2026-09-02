import { ChangeDetectionStrategy, Component, afterNextRender, booleanAttribute, computed, input, numberAttribute, signal } from '@angular/core';
import { TumUiSeverity, TumUiSeverityAlias, resolveSeverity } from '../foundation/tum-ui-vocabulary';

/** Severities a progress bar can carry. A subset of {@link TumUiSeverity}. */
export type TumUiProgressBarSeverity = Extract<TumUiSeverity, 'primary' | 'success' | 'warning' | 'danger' | 'info'>;

/**
 * A determinate meter: how far a value has travelled between a floor and a ceiling.
 *
 * It draws a proportion, so it may only be used where a real ceiling exists. Work whose total is unknown has no
 * proportion to draw, and a bar that invents one is a claim the surface cannot support — show the elapsed figure
 * and the stage instead.
 *
 * `min` and `max` are the scale, not decoration: without them a bar reporting "17 of 42" reaches assistive
 * technology as "40 percent", which is a number nobody on the surface can see. Supply `valueText` whenever the
 * figure has a unit or a denominator, so the announcement matches the words printed beside the bar.
 *
 * The fill transitions in 400 ms. At a full second the bar is still travelling when the next update lands, so it
 * permanently disagrees with the figure next to it. The very first committed value does not transition at all: a
 * page load that sweeps the bar from zero implies progress the reader did not witness.
 */
@Component({
    selector: 'tum-ui-progress-bar',
    templateUrl: './tum-ui-progress-bar.component.html',
    styleUrl: './tum-ui-progress-bar.component.scss',
    host: {
        class: 'tum-ui-progress-bar',
        role: 'progressbar',
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

    // The first value is painted without a transition, so the bar shows where the work already is rather than
    // animating there. Every value after it moves. Set after the first paint rather than in a timer, so the flag
    // is tied to the render that drew the initial width.
    private readonly firstPaintDone = signal(false);
    protected readonly committed = this.firstPaintDone.asReadonly();

    constructor() {
        afterNextRender(() => this.firstPaintDone.set(true));
    }
}
