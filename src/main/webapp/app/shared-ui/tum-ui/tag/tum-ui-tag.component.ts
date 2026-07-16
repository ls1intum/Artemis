import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Owned Artemis tag / status pill, part of the tum-aet-ui kit (future @tumaet/ui-angular).
 *
 * Pure-presentational element component (no behavior, no CDK). Styled with Artemis token utilities
 * to match the tinted PrimeNG p-tag look (state color at low opacity for the background, full color
 * for the label), dark-mode-correct for free. Secondary/contrast fall back to the surface ramp.
 */
export type TumUiTagSeverity = 'secondary' | 'success' | 'info' | 'warn' | 'danger' | 'contrast';

const TAG_BASE = 'inline-flex items-center gap-1 px-2 py-1 text-sm font-bold';

const TAG_SEVERITY: Record<TumUiTagSeverity, string> = {
    secondary: 'bg-surface-200 text-surface-700 dark:bg-surface-800 dark:text-surface-200',
    // State severities are colored in the component stylesheet (by data-severity) to match p-tag exactly.
    success: '',
    info: '',
    warn: '',
    danger: '',
    contrast: 'bg-surface-900 text-surface-0 dark:bg-surface-0 dark:text-surface-900',
};

@Component({
    selector: 'tum-ui-tag',
    templateUrl: './tum-ui-tag.component.html',
    styleUrl: './tum-ui-tag.component.scss',
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TumUiTagComponent {
    readonly severity = input<TumUiTagSeverity>('secondary');
    readonly value = input<string>();
    readonly rounded = input(false);

    protected readonly tagClasses = computed(() => `${TAG_BASE} ${this.rounded() ? 'rounded-full' : 'rounded-md'} ${TAG_SEVERITY[this.severity()]}`);
}
