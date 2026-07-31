import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Owned determinate progress bar, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Drop-in replacement for PrimeNG's `p-progressbar` in determinate mode: same track (height 1.25rem,
 * content-border-color background, 6px radius), the same animated `bg-primary` fill, and the same label
 * behavior reproduced from the Aura `progressbar` tokens + base style. The optional `color` overrides the
 * fill background (exactly like p-progressbar `[color]`, which the metrics blocks set to a semantic state
 * var). The percentage label follows p-progressbar precisely: the default `{value}{unit}` shows only when
 * `showValue` is true (and the value is non-zero), while projected content — the migration target of
 * p-progressbar's `#content` template — always renders inside the fill, regardless of `showValue`.
 */
@Component({
    selector: 'tum-ui-progress-bar',
    templateUrl: './tum-ui-progress-bar.component.html',
    styleUrl: './tum-ui-progress-bar.component.scss',
    host: {
        '[class]': 'hostClasses()',
        role: 'progressbar',
        '[attr.aria-valuemin]': '0',
        '[attr.aria-valuemax]': '100',
        '[attr.aria-valuenow]': 'value()',
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiProgressBarComponent {
    /** Current progress, 0..100 (drives the fill width and `aria-valuenow`). */
    readonly value = input<number>(0);
    /** Whether to render the default `{value}{unit}` label. Projected content ignores this (parity with p-progressbar). */
    readonly showValue = input(true);
    /** CSS color for the fill background; overrides the default `bg-primary` (drop-in for p-progressbar `[color]`). */
    readonly color = input<string>();
    /** Unit appended to the default value label (parity with p-progressbar `[unit]`). */
    readonly unit = input<string>('%');
    /** Extra classes forwarded onto the bar (drop-in for p-progressbar `styleClass`, e.g. `mb-2`). */
    readonly styleClass = input<string>('');

    // Track background = Aura's content.border.color (surface.200 light / surface.800 dark, matching the
    // Artemis theme override); the fill is bg-primary on the value element unless `color` overrides it inline.
    protected readonly hostClasses = computed(() => `tum-ui-progress-bar bg-surface-200 dark:bg-surface-800 ${this.styleClass()}`.trim());
}
