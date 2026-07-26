import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Owned indeterminate loading spinner, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Drop-in replacement for PrimeNG's `p-progressspinner`: same 100×100 box, the same rotating SVG circle
 * with the dash + color animations reproduced from the Aura `progressspinner` base style. PrimeNG's Aura
 * theme animates the stroke through four primitive ramps (red/blue/green/yellow); this kit keeps the same
 * four-stop rhythm but maps them to sanctioned semantic tokens (danger → primary → success → warning), so
 * the sweep stays on-brand and dark-mode-correct without touching `--p-*` primitives. Exposes `role="status"`
 * with an `aria-label` for assistive tech (the visible spinner carries no text).
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
    },
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiProgressSpinnerComponent {
    /** Width of the circle stroke (parity with p-progressspinner `[strokeWidth]`, default `'2'`). */
    readonly strokeWidth = input<string | number>('2');
    /** Fill of the circle interior (parity with p-progressspinner `[fill]`, default `'none'`). */
    readonly fill = input<string>('none');
    /** Duration of one full rotation (parity with p-progressspinner `[animationDuration]`, default `'2s'`). */
    readonly animationDuration = input<string>('2s');
    /** Accessible label describing what is loading (parity with p-progressspinner `[ariaLabel]`). */
    readonly ariaLabel = input<string>();
    /** Extra classes forwarded onto the spinner (drop-in for p-progressspinner `styleClass`, e.g. to resize). */
    readonly styleClass = input<string>('');

    protected readonly hostClasses = computed(() => `tum-ui-progress-spinner ${this.styleClass()}`.trim());
}
