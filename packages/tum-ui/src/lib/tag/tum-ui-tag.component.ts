import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type TumUiTagSeverity = 'secondary' | 'success' | 'info' | 'warn' | 'danger' | 'contrast';

const TAG_BASE = 'inline-flex items-center gap-1 px-2 py-1 text-sm font-bold';

const TAG_SEVERITY: Record<TumUiTagSeverity, string> = {
    secondary: 'bg-tum-ui-surface-200 text-tum-ui-surface-700 dark:bg-tum-ui-surface-800 dark:text-tum-ui-surface-200',
    success: '',
    info: '',
    warn: '',
    danger: '',
    contrast: 'bg-tum-ui-surface-900 text-tum-ui-surface-0 dark:bg-tum-ui-surface-0 dark:text-tum-ui-surface-900',
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

    readonly styleClass = input<string>('');

    protected readonly tagClasses = computed(() =>
        `${TAG_BASE} ${this.rounded() ? 'rounded-full' : 'rounded-md'} ${TAG_SEVERITY[this.severity()]} ${this.styleClass()}`.replace(/\s+/g, ' ').trim(),
    );
}
